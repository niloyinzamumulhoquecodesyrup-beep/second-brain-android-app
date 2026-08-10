package com.secondbrain.lock.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.secondbrain.lock.BuildConfig
import com.secondbrain.lock.data.FocusState
import com.secondbrain.lock.data.RoutineCache
import com.secondbrain.lock.data.SecurePrefs
import com.secondbrain.lock.network.dto.BriefingRequest
import com.secondbrain.lock.network.dto.BriefingResponse
import com.secondbrain.lock.network.dto.CommunityBookResponse
import com.secondbrain.lock.network.dto.CommunityBooksResponse
import com.secondbrain.lock.network.dto.CommunityMessageResponse
import com.secondbrain.lock.network.dto.CommunityMessagesResponse
import com.secondbrain.lock.network.dto.CommunitySuggestionResponse
import com.secondbrain.lock.network.dto.CommunitySuggestionsResponse
import com.secondbrain.lock.network.dto.HeartbeatRequest
import com.secondbrain.lock.network.dto.JoinRoomRequest
import com.secondbrain.lock.network.dto.JoinRoomResponse
import com.secondbrain.lock.network.dto.LeaveRoomRequest
import com.secondbrain.lock.network.dto.MindQueueAnswerRequest
import com.secondbrain.lock.network.dto.MindQueueItem
import com.secondbrain.lock.network.dto.MindcordDomainsResponse
import com.secondbrain.lock.network.dto.MindcordMessageResponse
import com.secondbrain.lock.network.dto.MindcordMessagesResponse
import com.secondbrain.lock.network.dto.MindcordParticipantsResponse
import com.secondbrain.lock.network.dto.NotificationPrefs
import com.secondbrain.lock.network.dto.OkResponse
import com.secondbrain.lock.network.dto.OtherBrainsClustersResponse
import com.secondbrain.lock.network.dto.OtherBrainsIdentityResponse
import com.secondbrain.lock.network.dto.Packet
import com.secondbrain.lock.network.dto.PlannerPromptRequest
import com.secondbrain.lock.network.dto.PostBodyRequest
import com.secondbrain.lock.network.dto.PostBookRequest
import com.secondbrain.lock.network.dto.Profile
import com.secondbrain.lock.network.dto.SendRoomMessageRequest
import com.secondbrain.lock.network.dto.SetDisplayNameRequest
import com.secondbrain.lock.network.dto.TurnCredentialsResponse
import com.secondbrain.lock.network.dto.UpdateProfileRequest
import com.secondbrain.lock.network.dto.VoiceClassifyRequest
import com.secondbrain.lock.network.dto.VoiceClassifyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Shared OkHttp client for all native API calls. Every request carries the bearer token from
 * [SecurePrefs] once logged in. A 401 from any endpoint clears the stored session and notifies
 * [onUnauthorized] so the UI can drop back to the login screen.
 */
object ApiClient {
    @PublishedApi internal val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    @PublishedApi internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val baseUrl: String get() = BuildConfig.BASE_URL

    private val unauthorizedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onUnauthorized: SharedFlow<Unit> = unauthorizedFlow

    @PublishedApi internal val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(::authInterceptor)
            .build()
    }

    private fun authInterceptor(chain: Interceptor.Chain): Response {
        val token = SecurePrefs.getToken(appContext)
        val original = chain.request()
        val request = if (token != null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        val response = chain.proceed(request)
        if (response.code == 401) {
            SecurePrefs.clearAuth(appContext)
            unauthorizedFlow.tryEmit(Unit)
        }
        return response
    }

    suspend fun login(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().put("email", email).put("password", password)
            val request = Request.Builder()
                .url("$baseUrl/api/auth/mobile")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException(errorMessage(response.code, bodyString))
                val obj = JSONObject(bodyString)
                val token = obj.getString("token")
                val respEmail = obj.optString("email", email)
                SecurePrefs.saveAuth(appContext, respEmail, token)
                token
            }
        }
    }

    /**
     * Creates a new account. `/api/auth/register` only sets a cookie and returns `{email}` (no
     * bearer token — it's the web login route's sibling), so the caller should follow success
     * with [login] using the same credentials to obtain the token native needs.
     */
    suspend fun register(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().put("email", email).put("password", password)
            val request = Request.Builder()
                .url("$baseUrl/api/auth/register")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException(errorMessage(response.code, bodyString))
            }
        }
    }

    fun logout() {
        SecurePrefs.clearAuth(appContext)
    }

    /** One-shot connectivity check (no persistent listener) — callers use this right after a
     * failed request to decide whether to queue it for later ([com.secondbrain.lock.data.SyncQueue])
     * instead of surfacing it as a real error. */
    fun isOffline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return true
        val capabilities = cm.getNetworkCapabilities(network) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ---- Account settings: profile, avatar, password, deactivation, quiet hours ----

    /** Caller should follow a success with [com.secondbrain.lock.data.repo.ProfileRepository.refresh]. */
    suspend fun updateProfileName(name: String): Result<Profile> =
        patchTyped("/api/auth/profile", UpdateProfileRequest(name))

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        val body = JSONObject().put("currentPassword", currentPassword).put("newPassword", newPassword)
        return postJson("/api/auth/password", body).map {}
    }

    /** [mimeType] e.g. "image/jpeg"; [base64Data] the raw (unprefixed) base64-encoded image bytes. */
    suspend fun uploadAvatar(mimeType: String, base64Data: String): Result<Unit> {
        val body = JSONObject().put("mime_type", mimeType).put("data", base64Data)
        return postJson("/api/auth/avatar", body).map {}
    }

    /** Signs out and blocks login server-side; the caller is still responsible for clearing the
     * locally stored token/session the same way a normal logout does. */
    suspend fun deactivateAccount(password: String): Result<Unit> {
        val body = JSONObject().put("password", password)
        return postJson("/api/auth/deactivate", body).map {}
    }

    /** Today's audio briefing — server enforces a once-per-UTC-day lock, no client-side force-regen.
     * [currentTimeMin] (minutes since midnight, phone's local wall clock) only shapes the result on
     * the first call of the day — once generated, that snapshot is baked into the cached audio for
     * the rest of the day, so it's safe (and expected) to pass it on every call regardless. */
    suspend fun getTodayBriefing(currentTimeMin: Int? = null): Result<BriefingResponse> =
        postTyped("/api/briefing/today", BriefingRequest(currentTimeMin))

    /** Read-only fetch of today's briefing if it's already been generated — 404s otherwise. Cannot
     * trigger generation itself (no path from this handler to Groq/Gemini), so it's safe to call
     * speculatively: as a POST-timeout fallback (the generation may have finished server-side even
     * though our connection didn't hear back), or for a voice command like "play my daily briefing"
     * that should only ever play an existing one, never kick off a new one. */
    suspend fun getCachedBriefing(): Result<BriefingResponse> = getTyped("/api/briefing/today")

    suspend fun getNotificationPrefs(): Result<NotificationPrefs> = getTyped("/api/notification-prefs")

    suspend fun updateNotificationPrefs(quietStartMin: Int?, quietEndMin: Int?): Result<NotificationPrefs> =
        putTyped("/api/notification-prefs", NotificationPrefs(quietStartMin, quietEndMin))

    /** GET an endpoint under [baseUrl], with the bearer token and shared cookies attached. */
    suspend fun get(path: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$baseUrl$path").get().build()
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    logFailure(request, response.code, bodyString)
                    throw IOException(errorMessage(response.code, bodyString))
                }
                if (bodyString.isBlank()) JSONObject() else JSONObject(bodyString)
            }
        }
    }

    private fun parseFocusState(obj: JSONObject): FocusState {
        if (!obj.optBoolean("active", false)) return FocusState.INACTIVE
        return FocusState(
            active = true,
            sessionId = obj.optString("session_id").ifEmpty { null },
            mode = obj.optString("mode").ifEmpty { null },
            startedAtMillis = obj.optString("started_at").ifEmpty { null }?.let { raw ->
                runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
            },
            endsAtMillis = obj.optString("ends_at").ifEmpty { null }?.let { raw ->
                runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
            },
            taskId = obj.optString("task_id").ifEmpty { null }
        )
    }

    /** Cross-device focus/Pomodoro state — active session (if any) started from any client. */
    suspend fun getFocusState(): Result<FocusState> = get("/api/focus/state").mapCatching(::parseFocusState)

    /** Starts a focus/Pomodoro session — this is exactly what [MonitorService][com.secondbrain.lock.service.MonitorService]'s
     * focus poll picks up to drive the Shield-side focus lock/overlay, from whichever client started it. */
    suspend fun startFocusSession(minutes: Int, taskId: String?, mode: String = "focus"): Result<FocusState> {
        val body = JSONObject().put("minutes", minutes).put("mode", mode)
        if (taskId != null) body.put("task_id", taskId)
        return postJson("/api/focus/state", body).mapCatching(::parseFocusState)
    }

    /** Cancels/ends the current focus session (defaults to marking it cancelled, not completed). */
    suspend fun cancelFocusSession(): Result<Unit> = deleteRaw("/api/focus/state")

    /**
     * Logs a completed focus session for streaks. [sessionId] makes this idempotent server-side
     * (via focus_sessions.activity_logged) — safe to call even if the client that started the
     * session, or another observing client, already reported the same completion.
     */
    suspend fun postFocusActivity(mode: String, minutes: Int, taskId: String?, sessionId: String?): Result<Unit> {
        val body = JSONObject().put("mode", mode).put("minutes", minutes)
        if (taskId != null) body.put("task_id", taskId)
        if (sessionId != null) body.put("session_id", sessionId)
        return postJson("/api/activity/focus", body).map {}
    }

    /**
     * Live "don't notify me right now" signal the reminders evaluator reads — distinct from
     * [postFocusActivity], which logs completed minutes after the fact. [endsAt] is an ISO-8601
     * instant string; only meaningful while [active] is true.
     */
    suspend fun postFocusState(active: Boolean, endsAt: String?): Result<Unit> {
        val body = JSONObject().put("active", active)
        if (active && endsAt != null) body.put("ends_at", endsAt)
        return postJson("/api/activity/focus-state", body).map {}
    }

    /** Records the user's free-text answer to the planner's standing "what's your routine?" prompt. */
    suspend fun postPlannerPrompt(text: String): Result<Unit> =
        postTyped<PlannerPromptRequest, OkResponse>("/api/planner/prompts", PlannerPromptRequest(text)).map {}

    /** "Your brain suggests" — the pending para_fun_queue rows; caller filters for create_task options. */
    suspend fun getMindQueue(): Result<List<MindQueueItem>> = getTyped("/api/mind/queue")

    /** Turns a distilled note into a reusable packet. */
    suspend fun createPacket(noteId: String, title: String?, content: String?): Result<Unit> {
        val body = JSONObject().put("note_id", noteId)
        if (title != null) body.put("title", title)
        if (content != null) body.put("content", content)
        return postJson("/api/packets", body).map {}
    }

    /** All packets distilled so far — created via [createPacket] but never listed anywhere. */
    suspend fun getPackets(): Result<List<Packet>> = getTyped("/api/packets")

    /** Sends the on-device [SpeechRecognizer][android.speech.SpeechRecognizer] transcript from the
     * shield button's long-press for the backend to classify into a task or a PARA capture. */
    suspend fun classifyVoice(transcript: String): Result<VoiceClassifyResponse> =
        postTyped("/api/voice/classify", VoiceClassifyRequest(transcript))

    /** Accept (action="create_task", value={title}) or dismiss (action="skip") a queue item. */
    suspend fun answerMindQueue(id: String, request: MindQueueAnswerRequest): Result<OkResponse> =
        postTyped("/api/mind/queue/$id/answer", request)

    // ---- MINDVERSE: Other Brains (cross-account, anonymous community feed) ----

    suspend fun getOtherBrainsIdentity(): Result<OtherBrainsIdentityResponse> = getTyped("/api/other-brains/identity")
    suspend fun setOtherBrainsIdentity(displayName: String): Result<OtherBrainsIdentityResponse> =
        postTyped("/api/other-brains/identity", SetDisplayNameRequest(displayName))

    suspend fun getCommunityMessages(): Result<CommunityMessagesResponse> = getTyped("/api/other-brains/messages")
    suspend fun postCommunityMessage(body: String): Result<CommunityMessageResponse> =
        postTyped("/api/other-brains/messages", PostBodyRequest(body))

    suspend fun getCommunitySuggestions(): Result<CommunitySuggestionsResponse> = getTyped("/api/other-brains/suggestions")
    suspend fun postCommunitySuggestion(body: String): Result<CommunitySuggestionResponse> =
        postTyped("/api/other-brains/suggestions", PostBodyRequest(body))

    suspend fun getCommunityBooks(): Result<CommunityBooksResponse> = getTyped("/api/other-brains/books")
    suspend fun postCommunityBook(title: String, note: String): Result<CommunityBookResponse> =
        postTyped("/api/other-brains/books", PostBookRequest(title, note))

    // ---- MINDVERSE: Mindcord (live, room-based chat) ----

    suspend fun getMindcordDomains(): Result<MindcordDomainsResponse> = getTyped("/api/mindcord/rooms")
    suspend fun joinMindcordRoom(domain: String): Result<JoinRoomResponse> = postTyped("/api/mindcord/join", JoinRoomRequest(domain))
    suspend fun leaveMindcordRoom(roomId: String): Result<OkResponse> = postTyped("/api/mindcord/leave", LeaveRoomRequest(roomId))
    suspend fun getMindcordParticipants(roomId: String): Result<MindcordParticipantsResponse> =
        getTyped("/api/mindcord/participants?room_id=$roomId")
    suspend fun getMindcordMessages(roomId: String): Result<MindcordMessagesResponse> =
        getTyped("/api/mindcord/messages?room_id=$roomId")
    suspend fun sendMindcordMessage(roomId: String, body: String): Result<MindcordMessageResponse> =
        postTyped("/api/mindcord/messages", SendRoomMessageRequest(roomId, body))

    /** Keeps a room participant's `last_seen_at` fresh — called every ~20s while actually in a
     * room, so the server's stale-participant expiry (>45s) doesn't silently boot an active user. */
    suspend fun postMindcordHeartbeat(roomId: String): Result<Unit> =
        postTyped<HeartbeatRequest, OkResponse>("/api/mindcord/heartbeat", HeartbeatRequest(roomId)).map {}

    /** Short-lived STUN+TURN server list for the WebRTC mesh — fetch once per room join (shared
     * by every [org.webrtc.PeerConnection] in that room), never per-peer-connection. */
    suspend fun getMindcordTurnCredentials(): Result<TurnCredentialsResponse> =
        getTyped("/api/mindcord/turn-credentials")

    /** Aggregate-only "who's studying what": domain name + distinct-account headcount. */
    suspend fun getOtherBrainsClusters(): Result<OtherBrainsClustersResponse> = getTyped("/api/other-brains/clusters")

    /** GET an endpoint under [baseUrl] that responds with a JSON array. */
    suspend fun getArray(path: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$baseUrl$path").get().build()
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException(errorMessage(response.code, bodyString))
                if (bodyString.isBlank()) JSONArray() else JSONArray(bodyString)
            }
        }
    }

    /** The user's planner routines — used for schedule auto-block windows and the sectograph widget. */
    suspend fun getPlannerRoutines(): Result<List<RoutineCache>> = getArray("/api/planner/routines").map { array ->
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val daysArray = obj.optJSONArray("days")
            val days = if (daysArray != null) {
                (0 until daysArray.length()).joinToString(",") { daysArray.getInt(it).toString() }
            } else {
                "0,1,2,3,4,5,6"
            }
            RoutineCache(
                id = obj.getString("id"),
                title = obj.optString("title", "Routine"),
                category = obj.optString("category", "other"),
                days = days,
                startMin = obj.optInt("start_min", 0),
                durationMin = obj.optInt("duration_min", 0),
                active = obj.optBoolean("active", true)
            )
        }
    }

    /** POST a JSON body to an endpoint under [baseUrl], with the bearer token attached. */
    suspend fun postJson(path: String, body: JSONObject): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl$path")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    logFailure(request, response.code, bodyString)
                    throw IOException(errorMessage(response.code, bodyString))
                }
                if (bodyString.isBlank()) JSONObject() else JSONObject(bodyString)
            }
        }
    }

    @PublishedApi internal fun errorMessage(code: Int, body: String): String {
        val detail = runCatching { JSONObject(body).optString("error") }.getOrNull()
        return if (!detail.isNullOrBlank()) detail else "Request failed ($code)"
    }

    /** Debug-only: every non-2xx response gets logged with its method/URL/body so a failure can be
     * traced back to the exact call that caused it — there's no request/response logging otherwise,
     * so a bare "Request failed (500)" surfaced in the UI is otherwise a dead end to diagnose. */
    private fun logFailure(request: Request, code: Int, body: String) {
        if (BuildConfig.DEBUG) {
            android.util.Log.w("ApiClient", "${request.method} ${request.url} -> $code: $body")
        }
    }

    // ---- kotlinx.serialization-based typed helpers, used by the repository layer ----
    // Kept alongside the org.json helpers above (used by FocusState/RoutineCache, which power
    // the already-shipping Shield/blocking subsystem) rather than replacing them, so nothing
    // that already works can regress while the rest of the app is ported screen by screen.

    @PublishedApi internal suspend fun executeRaw(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                logFailure(request, response.code, bodyString)
                throw IOException(errorMessage(response.code, bodyString))
            }
            bodyString
        }
    }

    /** GET [path] and decode the JSON response body as [T]. */
    suspend inline fun <reified T> getTyped(path: String): Result<T> = runCatching {
        val request = Request.Builder().url("$baseUrl$path").get().build()
        json.decodeFromString(executeRaw(request))
    }

    /** POST [body] (any @Serializable type) to [path], decoding the response as [T]. */
    suspend inline fun <reified B, reified T> postTyped(path: String, body: B): Result<T> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(json.encodeToString(body).toRequestBody(jsonMediaType))
            .build()
        json.decodeFromString(executeRaw(request))
    }

    /** POST with no request body, decoding the response as [T]. */
    suspend inline fun <reified T> postTyped(path: String): Result<T> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post("{}".toRequestBody(jsonMediaType))
            .build()
        json.decodeFromString(executeRaw(request))
    }

    /** PUT [body] (any @Serializable type) to [path], decoding the response as [T]. */
    suspend inline fun <reified B, reified T> putTyped(path: String, body: B): Result<T> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .put(json.encodeToString(body).toRequestBody(jsonMediaType))
            .build()
        json.decodeFromString(executeRaw(request))
    }

    /** PATCH [body] (any @Serializable type) to [path], decoding the response as [T]. */
    suspend inline fun <reified B, reified T> patchTyped(path: String, body: B): Result<T> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .patch(json.encodeToString(body).toRequestBody(jsonMediaType))
            .build()
        json.decodeFromString(executeRaw(request))
    }

    /** DELETE [path]. Response body (if any) is ignored — callers just care whether it threw. */
    suspend fun deleteRaw(path: String): Result<Unit> = runCatching {
        val request = Request.Builder().url("$baseUrl$path").delete().build()
        executeRaw(request)
        Unit
    }
}
