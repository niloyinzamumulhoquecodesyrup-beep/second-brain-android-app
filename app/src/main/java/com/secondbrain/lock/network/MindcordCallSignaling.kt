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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val TAG = "MindcordCallSignal"

/**
 * Supabase Realtime "broadcast" channel — the WebRTC signaling transport for Mindcord's voice/
 * video mesh. Unlike [MindcordRealtime] (postgres_changes, backed by real tables), broadcast
 * messages are ephemeral: the server just relays whatever payload one client sends to every
 * other client currently joined to the same channel topic, no database involved. Every signal
 * payload this app sends/receives carries explicit from/to user_ids so a peer can ignore traffic
 * not addressed to it (the channel itself has no per-recipient routing).
 *
 * Deliberately a separate class/topic (`mindcord_call:<room_id>`) from [MindcordRealtime]'s
 * `mindcord_room_<room_id>` — mixing postgres_changes and broadcast configs on one channel works
 * too, but keeping signaling traffic on its own topic means a call-signaling bug can't wedge the
 * message/presence subscription and vice versa.
 */
class MindcordCallSignaling(
    private val onSignal: (JsonObject) -> Unit
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
    private var joined = false

    fun connect(roomId: String) {
        disconnect()
        val channelTopic = "realtime:mindcord_call:$roomId"
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
                webSocket.send(joinFrame(channelTopic).toString())
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "socket failure: ${t.message}")
                joined = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "socket closed: $code $reason")
                joined = false
            }
        })
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        socket?.close(1000, "call ended")
        socket = null
        topic = null
        joined = false
    }

    /** Fire-and-forget — a dropped signal (socket not yet joined, a peer who's already gone)
     * just means that one negotiation step never lands; the caller's own retry-by-re-offering
     * on the next participants refresh is the recovery path, not anything in here. */
    fun send(payload: JsonObject) {
        val currentTopic = topic ?: return
        val frame = buildJsonObject {
            put("topic", currentTopic)
            put("event", "broadcast")
            putJsonObject("payload") {
                put("type", "broadcast")
                put("event", "signal")
                put("payload", payload)
            }
            put("ref", refCounter.getAndIncrement().toString())
        }
        socket?.send(frame.toString())
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()
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

    private fun joinFrame(channelTopic: String) = buildJsonObject {
        put("topic", channelTopic)
        put("event", "phx_join")
        putJsonObject("payload") {
            putJsonObject("config") {
                putJsonObject("broadcast") {
                    put("self", false)
                    put("ack", false)
                }
            }
            put("access_token", BuildConfig.SUPABASE_ANON_KEY)
        }
        val ref = refCounter.getAndIncrement().toString()
        put("ref", ref)
        put("join_ref", ref)
    }

    private fun handleFrame(text: String) {
        runCatching {
            val frame = json.parseToJsonElement(text).jsonObject
            val event = frame["event"]?.jsonPrimitive?.content ?: return
            when (event) {
                "phx_reply" -> {
                    val replyTopic = frame["topic"]?.jsonPrimitive?.content
                    if (replyTopic != "phoenix") {
                        joined = frame["payload"]?.jsonObject?.get("status")?.jsonPrimitive?.content == "ok"
                        Log.d(TAG, "join reply: $joined")
                    }
                }
                "broadcast" -> {
                    val inner = frame["payload"]?.jsonObject ?: return
                    val signalPayload = inner["payload"]?.jsonObject ?: return
                    onSignal(signalPayload)
                }
            }
        }.onFailure { Log.w(TAG, "frame parse failed: ${it.message} | $text") }
    }
}
