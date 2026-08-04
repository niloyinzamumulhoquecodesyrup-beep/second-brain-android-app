package com.secondbrain.lock.network

import android.util.Log
import com.secondbrain.lock.BuildConfig
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val TAG = "MindcordRealtime"

/**
 * A minimal client for Supabase Realtime's Phoenix-channel websocket protocol, scoped to exactly
 * what a Mindcord room needs: one channel, subscribed to postgres_changes on mindcord_messages
 * (INSERT) and mindcord_participants (*), both filtered to this room_id. Deliberately not a
 * general-purpose Realtime SDK — the official supabase-kt pulls in Ktor as a second HTTP stack
 * alongside the OkHttp this app already builds everything else on, for what's otherwise a single
 * narrow subscription.
 *
 * Talks directly to the anon-key-scoped websocket (never [ApiClient]'s client/interceptor — that
 * attaches this app's own backend bearer token, which Supabase would reject as an invalid JWT for
 * its own auth). No reconnect-with-backoff: a dropped socket just means the caller's own fallback
 * poll (see MindverseRoomScreen) keeps messages/participants eventually-consistent until the next
 * room join reconnects it — acceptable for a latency optimization, not the source of truth.
 */
class MindcordRealtime(
    /** table (e.g. "mindcord_messages"), eventType (e.g. "INSERT"/"UPDATE"/"DELETE"), new-row JSON (null for DELETE) */
    private val onChange: (table: String, eventType: String, newRow: JsonObject?) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val refCounter = AtomicInteger(1)
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisor)
    private var heartbeatJob: Job? = null
    private var socket: WebSocket? = null
    private var topic: String? = null

    fun connect(roomId: String) {
        disconnect()
        val channelTopic = "realtime:mindcord_room_$roomId"
        topic = channelTopic
        val wsUrl = BuildConfig.SUPABASE_URL.replaceFirst("http", "ws") +
            "/realtime/v1/websocket?apikey=${BuildConfig.SUPABASE_ANON_KEY}&vsn=1.0.0"
        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "connected, joining $channelTopic")
                webSocket.send(joinFrame(channelTopic, roomId).toString())
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "socket failure: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "socket closed: $code $reason")
            }
        })
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        socket?.close(1000, "leaving room")
        socket = null
        topic = null
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
        // Phoenix (the channel protocol Supabase Realtime speaks) drops a connection that goes
        // quiet — this keeps it alive independent of Mindcord's own 20s room-presence heartbeat.
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(25_000)
                webSocket.send(
                    buildJsonObject {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        putJsonObject("payload") {}
                        put("ref", refCounter.getAndIncrement().toString())
                    }.toString()
                )
            }
        }
    }

    private fun joinFrame(channelTopic: String, roomId: String) = buildJsonObject {
        put("topic", channelTopic)
        put("event", "phx_join")
        putJsonObject("payload") {
            putJsonObject("config") {
                putJsonArray("postgres_changes") {
                    add(buildJsonObject {
                        put("event", "INSERT")
                        put("schema", "public")
                        put("table", "mindcord_messages")
                        put("filter", "room_id=eq.$roomId")
                    })
                    add(buildJsonObject {
                        put("event", "*")
                        put("schema", "public")
                        put("table", "mindcord_participants")
                        put("filter", "room_id=eq.$roomId")
                    })
                }
            }
            put("access_token", BuildConfig.SUPABASE_ANON_KEY)
        }
        val ref = refCounter.getAndIncrement().toString()
        put("ref", ref)
        put("join_ref", ref)
    }

    // The exact Phoenix/Realtime wire shape here is built from protocol knowledge, not a fixture
    // — wrapped defensively end-to-end so a shape surprise from the live server logs and drops
    // that one frame instead of tearing down the socket (the fallback poll covers the gap either
    // way).
    private fun handleFrame(text: String) {
        runCatching {
            val frame = json.parseToJsonElement(text).jsonObject
            val event = frame["event"]?.jsonPrimitive?.contentOrNull ?: return
            when (event) {
                "phx_reply" -> {
                    val status = frame["payload"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
                    val replyTopic = frame["topic"]?.jsonPrimitive?.contentOrNull
                    Log.d(TAG, "${if (replyTopic == "phoenix") "heartbeat" else "join"} reply: $status")
                }
                "postgres_changes" -> {
                    // Confirmed against the live server (not the realtime-js docs' shape, which
                    // describe eventType/new): the wire payload uses "type" for the change kind
                    // and "record" for the row, e.g. {"table":"mindcord_participants",
                    // "type":"UPDATE","record":{...},"old_record":{"id":"..."},...}.
                    val data = frame["payload"]?.jsonObject?.get("data")?.jsonObject ?: return
                    val table = data["table"]?.jsonPrimitive?.contentOrNull ?: return
                    val eventType = data["type"]?.jsonPrimitive?.contentOrNull ?: return
                    val newRow = data["record"]?.jsonObject?.takeIf { it.isNotEmpty() }
                    Log.d(TAG, "change: $table $eventType $newRow")
                    onChange(table, eventType, newRow)
                }
                "system" -> Log.d(TAG, "system: $frame")
                else -> {}
            }
        }.onFailure { Log.w(TAG, "frame parse failed: ${it.message} | $text") }
    }
}
