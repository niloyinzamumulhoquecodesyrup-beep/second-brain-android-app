package com.secondbrain.lock.webrtc

import android.content.Context
import android.util.Log
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.MindcordCallSignaling
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RTCStatsReport
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

private const val TAG = "MindcordCallManager"
private const val STREAM_ID = "mindcord0"
private const val AUDIO_TRACK_ID = "mindcord_audio0"
private const val AUDIO_LEVEL_POLL_MS = 300L
private const val OFFER_RETRY_CHECK_MS = 4_000L
private const val OFFER_RETRY_TIMEOUT_MS = 8_000L
// WebRTC's stats "audioLevel" is normalized 0.0-1.0 (roughly RMS); ambient mic noise floor sits
// well under this even in a quiet room, actual speech reliably clears it.
private const val SPEAKING_THRESHOLD = 0.02
private const val VIDEO_TRACK_ID = "mindcord_video0"

/**
 * The Mindcord voice/video mesh: one [PeerConnection] per other participant. STUN-only ICE left
 * two devices without a direct network path stuck in CHECKING→FAILED (no relay candidate ever
 * gathered), so [start] fetches a short-lived STUN+TURN mix from `/api/mindcord/turn-credentials`
 * (backend-side Metered account, secret key never touches the client) before opening any
 * [PeerConnection] — see [fetchIceServers]. If that fetch fails, [iceServers] falls back to
 * Google's public STUN server alone, same "some joins won't connect on symmetric NAT" gap the web
 * client already accepts, rather than blocking the call entirely. Offerer is decided
 * deterministically by comparing user_ids (higher id offers) so both sides never simultaneously
 * offer (glare).
 *
 * Local audio/video tracks are created once, up front, and added to every peer connection at
 * creation time — even before the user has turned mic/camera on. Muting/unmuting or toggling the
 * camera is then just [AudioTrack.setEnabled]/starting-or-stopping the capturer on a track that
 * already exists in every connection's SDP, which avoids renegotiation entirely (adding or
 * removing a track after the initial offer/answer would need a fresh offer/answer round). A
 * disabled/uncaptured track sends nothing, but the remote side has no way to tell "disabled" apart
 * from "briefly stalled" from the media alone — [remoteVideoOn] is fed by an explicit signal
 * (kind="video-state") for exactly that reason, not inferred from the stream.
 *
 * Owned by the room screen (via `remember`), not [com.secondbrain.lock.data.repo.MindverseRepository]
 * — it needs an Android [Context] for the camera/eglBase and a clear dispose point tied to leaving
 * the screen, neither of which fit a process-lifetime singleton.
 */
class MindcordCallManager(private val context: Context, private val localUserId: String) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val signaling = MindcordCallSignaling(onSignal = ::onSignal)

    val eglBase: EglBase = EglBase.create()

    private var factory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var started = false

    private class Peer(val connection: PeerConnection, val createdAtMs: Long = System.currentTimeMillis()) {
        val pendingRemoteIce = mutableListOf<IceCandidate>()
        var remoteDescriptionSet = false
    }

    private val peers = mutableMapOf<String, Peer>()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val remoteVideoTracks: StateFlow<Map<String, VideoTrack>> = _remoteVideoTracks.asStateFlow()

    private val _remoteVideoOn = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val remoteVideoOn: StateFlow<Map<String, Boolean>> = _remoteVideoOn.asStateFlow()

    private val _connectedPeerIds = MutableStateFlow<Set<String>>(emptySet())
    val connectedPeerIds: StateFlow<Set<String>> = _connectedPeerIds.asStateFlow()

    // Driven by real WebRTC stats ("media-source"/"inbound-rtp" audioLevel — see
    // [pollAudioLevels]), not by mic-enabled/track-present state, so the room UI's waveform only
    // animates while someone is actually making sound, not just because their mic is unmuted.
    private val _localSpeaking = MutableStateFlow(false)
    val localSpeaking: StateFlow<Boolean> = _localSpeaking.asStateFlow()

    private val _remoteSpeaking = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val remoteSpeaking: StateFlow<Map<String, Boolean>> = _remoteSpeaking.asStateFlow()

    private var audioLevelJob: Job? = null
    private var offerRetryJob: Job? = null

    private val _remoteHandRaised = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val remoteHandRaised: StateFlow<Map<String, Boolean>> = _remoteHandRaised.asStateFlow()

    // Mirrors what's been broadcast so a peer who connects *after* you've already turned your
    // camera on / raised your hand — not just one who was present when you toggled it — still
    // learns your current state (sent once, directly to them, right as their connection goes
    // CONNECTED; see createPeer's onIceConnectionChange).
    private var cameraOnState = false
    private var handRaisedState = false

    private val fallbackIceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    // Replaced by [fetchIceServers] right after [start] before any peer connection opens; stays
    // at the STUN-only fallback only if that fetch fails.
    private var iceServers: List<PeerConnection.IceServer> = fallbackIceServers

    // [syncPeers] is driven by a separate Compose effect (LaunchedEffect(others) in the room
    // screen) than [start], so it can otherwise race ahead of the ICE-credential fetch below and
    // open a peer connection against the STUN-only fallback while the real fetch is still in
    // flight. Both start()'s own doSyncPeers call and every external syncPeers() call wait on
    // this instead.
    private val iceServersReady = CompletableDeferred<Unit>()

    private fun rtcConfig() = PeerConnection.RTCConfiguration(iceServers).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
    }

    /** One fetch per room join (not per-peer-connection) — every [PeerConnection] opened in this
     * room shares the same resolved server list. */
    private suspend fun fetchIceServers() {
        ApiClient.getMindcordTurnCredentials()
            .onSuccess { response ->
                val servers = response.iceServers.mapNotNull { entry ->
                    if (entry.urls.isBlank()) return@mapNotNull null
                    val builder = PeerConnection.IceServer.builder(entry.urls)
                    entry.username?.let { builder.setUsername(it) }
                    entry.credential?.let { builder.setPassword(it) }
                    builder.createIceServer()
                }
                if (servers.isNotEmpty()) iceServers = servers
            }
            .onFailure { Log.w(TAG, "turn credentials fetch failed, using STUN-only fallback: ${it.message}") }
        iceServersReady.complete(Unit)
    }

    /** Requires RECORD_AUDIO/CAMERA to already be granted for the corresponding track to
     * actually produce anything — safe to call without either; that track is just silent/blank. */
    fun start(roomId: String, hasMicPermission: Boolean, hasCameraPermission: Boolean) {
        if (started) return
        started = true
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        val pcFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        factory = pcFactory

        if (hasMicPermission) {
            runCatching {
                val source = pcFactory.createAudioSource(MediaConstraints())
                audioSource = source
                localAudioTrack = pcFactory.createAudioTrack(AUDIO_TRACK_ID, source).apply { setEnabled(false) }
            }.onFailure { Log.w(TAG, "audio track init failed: ${it.message}") }
        }

        if (hasCameraPermission) {
            runCatching {
                val enumerator = Camera2Enumerator(context)
                val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                    ?: enumerator.deviceNames.firstOrNull()
                if (deviceName != null) {
                    val videoCapturer = enumerator.createCapturer(deviceName, null)
                    val helper = SurfaceTextureHelper.create("MindcordCapture", eglBase.eglBaseContext)
                    surfaceTextureHelper = helper
                    val source = pcFactory.createVideoSource(videoCapturer.isScreencast)
                    videoCapturer.initialize(helper, context, source.capturerObserver)
                    capturer = videoCapturer
                    videoSource = source
                    localVideoTrack = pcFactory.createVideoTrack(VIDEO_TRACK_ID, source).apply { setEnabled(true) }
                    _localVideoTrack.value = localVideoTrack
                }
            }.onFailure { Log.w(TAG, "camera init failed: ${it.message}") }
        }

        // Signaling only opens (and thus can only trigger createPeer, via an incoming offer or
        // doSyncPeers below) once the ICE server list is resolved — no peer connection in this
        // room is ever built against the STUN-only fallback unless the fetch itself failed.
        scope.launch {
            fetchIceServers()
            signaling.connect(roomId)
            doSyncPeers(lastKnownParticipants)
        }

        startAudioLevelPolling()
        startOfferRetryWatchdog()
    }

    private fun startOfferRetryWatchdog() {
        offerRetryJob?.cancel()
        offerRetryJob = scope.launch {
            while (isActive) {
                delay(OFFER_RETRY_CHECK_MS)
                retryStalledOffers()
            }
        }
    }

    /** Broadcast signaling has no delivery guarantee or replay — if our offer went out before the
     * other side's socket had joined the channel (a common race: whoever joins the room a beat
     * after someone already there connects signaling just after that first offer went out), it's
     * silently dropped with nothing to retry it, despite [doSyncPeers]'s `it !in peers` check
     * implying a later roster refresh would. Any peer we're the deterministic offerer for that's
     * sat unanswered past the timeout gets torn down and re-offered on the next sync. */
    private fun retryStalledOffers() {
        val now = System.currentTimeMillis()
        val stale = peers.filter { (remoteId, peer) ->
            localUserId > remoteId && !peer.remoteDescriptionSet && now - peer.createdAtMs > OFFER_RETRY_TIMEOUT_MS
        }.keys.toList()
        if (stale.isEmpty()) return
        Log.d(TAG, "retrying stalled offer(s) to $stale")
        stale.forEach { removePeer(it) }
        doSyncPeers(lastKnownParticipants)
    }

    private fun startAudioLevelPolling() {
        audioLevelJob?.cancel()
        audioLevelJob = scope.launch {
            while (isActive) {
                pollAudioLevels()
                delay(AUDIO_LEVEL_POLL_MS)
            }
        }
    }

    /** Local level comes from any one connection's "media-source" stats (same captured track is
     * attached to every [PeerConnection], so any of them reports it); remote levels come from
     * each connection's own "inbound-rtp" audio stats. Both empty/false whenever there's no
     * active peer connection yet — nothing to poll. */
    private suspend fun pollAudioLevels() {
        var localLevel = 0.0
        val remoteLevels = mutableMapOf<String, Boolean>()
        peers.forEach { (remoteId, peer) ->
            val report = runCatching { peer.connection.getStatsSuspend() }.getOrNull() ?: return@forEach
            report.statsMap.values.forEach { stat ->
                val kind = stat.members["kind"] as? String
                if (kind != "audio") return@forEach
                when (stat.type) {
                    "media-source" -> (stat.members["audioLevel"] as? Double)?.let { level ->
                        if (level > localLevel) localLevel = level
                    }
                    "inbound-rtp" -> {
                        val level = stat.members["audioLevel"] as? Double ?: 0.0
                        remoteLevels[remoteId] = level > SPEAKING_THRESHOLD
                    }
                }
            }
        }
        _localSpeaking.value = localLevel > SPEAKING_THRESHOLD
        _remoteSpeaking.value = remoteLevels
    }

    private fun awaitIceServersThenSync(remoteUserIds: List<String>) {
        scope.launch {
            iceServersReady.await()
            doSyncPeers(remoteUserIds)
        }
    }

    fun setMicEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    /** No-op (with a log) if the camera track never initialized — e.g. permission wasn't granted
     * at [start] time. The room screen re-requests permission and doesn't call this until granted. */
    fun setCameraEnabled(enabled: Boolean) {
        val videoCapturer = capturer
        if (videoCapturer == null) {
            Log.w(TAG, "setCameraEnabled($enabled) ignored — camera never initialized")
            return
        }
        runCatching {
            if (enabled) videoCapturer.startCapture(640, 480, 30) else videoCapturer.stopCapture()
        }.onFailure { Log.w(TAG, "camera ${if (enabled) "start" else "stop"} failed: ${it.message}") }
        localVideoTrack?.setEnabled(enabled)
        cameraOnState = enabled
        broadcastVideoState(enabled)
    }

    /** Hand-raise has no server-side record (unlike mic/camera, which at least reflect real
     * local track state even without a peer) — it only exists as this broadcast signal, so a
     * client that never established any peer connection never sees anyone else's raised hand. */
    fun setHandRaised(raised: Boolean) {
        handRaisedState = raised
        signaling.send(buildJsonObject {
            put("kind", "hand-raised")
            put("from", localUserId)
            put("raised", raised)
        })
    }

    // The room screen's participant-list effect and its permission-request effect race: the
    // roster can update (and call syncPeers) before the async permission dialog resolves and
    // start() actually runs createPeerConnectionFactory — every caller here is a fresh
    // coroutine/callback, so there's no ordering guarantee between them. Caching the last-seen
    // roster and re-running the sync once start() finishes (see below) means an early syncPeers
    // call is deferred, not dropped or (as it was — this crashed the app on a real device the
    // first time it hit that ordering) allowed to touch a null factory.
    private var lastKnownParticipants: List<String> = emptyList()

    /** Called whenever the room's participant roster changes — opens a peer connection (and
     * offers) for anyone new where this client is the deterministic offerer, and tears down
     * connections for anyone no longer in the room. */
    fun syncPeers(remoteUserIds: List<String>) {
        lastKnownParticipants = remoteUserIds
        if (!started) return
        awaitIceServersThenSync(remoteUserIds)
    }

    private fun doSyncPeers(remoteUserIds: List<String>) {
        val wanted = remoteUserIds.filter { it.isNotBlank() && it != localUserId }.toSet()
        (peers.keys - wanted).forEach { removePeer(it) }
        wanted.filter { it !in peers && localUserId > it }.forEach { remoteId ->
            scope.launch { offerTo(remoteId) }
        }
    }

    fun close() {
        audioLevelJob?.cancel()
        offerRetryJob?.cancel()
        peers.keys.toList().forEach { removePeer(it) }
        capturer?.let { runCatching { it.stopCapture() } }
        capturer?.dispose()
        surfaceTextureHelper?.dispose()
        localVideoTrack?.dispose()
        videoSource?.dispose()
        localAudioTrack?.dispose()
        audioSource?.dispose()
        factory?.dispose()
        eglBase.release()
        signaling.disconnect()
        started = false
    }

    private fun removePeer(remoteId: String) {
        peers.remove(remoteId)?.connection?.close()
        _remoteVideoTracks.update { it - remoteId }
        _remoteVideoOn.update { it - remoteId }
        _remoteHandRaised.update { it - remoteId }
        _connectedPeerIds.update { it - remoteId }
        _remoteSpeaking.update { it - remoteId }
    }

    private fun createPeer(remoteId: String): Peer {
        val pcFactory = factory ?: error("start() not called")
        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                // "typ host/srflx/relay" in the SDP string is the only cheap way to tell candidate
                // kind from the Java API without parsing further — logged to diagnose whether a
                // relay (TURN) candidate is even being gathered at all, vs. gathered-but-still-
                // failing-to-connect (those are different problems with different fixes).
                Log.d(TAG, "local candidate for $remoteId: ${candidate.sdp}")
                signaling.send(buildJsonObject {
                    put("kind", "ice")
                    put("from", localUserId)
                    put("to", remoteId)
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("candidate", candidate.sdp)
                })
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "peer $remoteId ice state: $state")
                val connected = state == PeerConnection.IceConnectionState.CONNECTED ||
                    state == PeerConnection.IceConnectionState.COMPLETED
                val wasConnected = remoteId in _connectedPeerIds.value
                _connectedPeerIds.update { if (connected) it + remoteId else it - remoteId }
                if (connected && !wasConnected) sendCurrentStateTo(remoteId)
            }

            override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track is VideoTrack) {
                    _remoteVideoTracks.update { it + (remoteId to track) }
                }
            }

            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                Log.d(TAG, "peer $remoteId gathering state: $state")
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        }
        val connection = pcFactory.createPeerConnection(rtcConfig(), observer)
            ?: error("createPeerConnection returned null")
        localAudioTrack?.let { connection.addTrack(it, listOf(STREAM_ID)) }
        localVideoTrack?.let { connection.addTrack(it, listOf(STREAM_ID)) }
        val peer = Peer(connection)
        peers[remoteId] = peer
        return peer
    }

    private suspend fun offerTo(remoteId: String) {
        val peer = peers[remoteId] ?: createPeer(remoteId)
        runCatching {
            val offer = peer.connection.createOfferSuspend(MediaConstraints())
            peer.connection.setLocalDescriptionSuspend(offer)
            signaling.send(buildJsonObject {
                put("kind", "offer")
                put("from", localUserId)
                put("to", remoteId)
                put("sdp", offer.description)
            })
        }.onFailure { Log.w(TAG, "offer to $remoteId failed: ${it.message}") }
    }

    private fun onSignal(payload: JsonObject) {
        val to = payload["to"]?.jsonPrimitive?.contentOrNull
        if (to != null && to != localUserId) return
        val from = payload["from"]?.jsonPrimitive?.contentOrNull ?: return
        if (from == localUserId) return
        when (payload["kind"]?.jsonPrimitive?.contentOrNull) {
            "offer" -> {
                val sdp = payload["sdp"]?.jsonPrimitive?.contentOrNull ?: return
                scope.launch { handleOffer(from, sdp) }
            }
            "answer" -> {
                val sdp = payload["sdp"]?.jsonPrimitive?.contentOrNull ?: return
                scope.launch { handleAnswer(from, sdp) }
            }
            "ice" -> {
                val sdpMid = payload["sdpMid"]?.jsonPrimitive?.contentOrNull
                val sdpMLineIndex = payload["sdpMLineIndex"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return
                val candidateSdp = payload["candidate"]?.jsonPrimitive?.contentOrNull ?: return
                handleIce(from, IceCandidate(sdpMid, sdpMLineIndex, candidateSdp))
            }
            "video-state" -> {
                val on = payload["on"]?.jsonPrimitive?.contentOrNull.toBoolean()
                _remoteVideoOn.update { it + (from to on) }
            }
            "hand-raised" -> {
                val raised = payload["raised"]?.jsonPrimitive?.contentOrNull.toBoolean()
                _remoteHandRaised.update { it + (from to raised) }
            }
        }
    }

    private suspend fun handleOffer(from: String, sdp: String) {
        val peer = peers[from] ?: createPeer(from)
        runCatching {
            peer.connection.setRemoteDescriptionSuspend(SessionDescription(SessionDescription.Type.OFFER, sdp))
            peer.remoteDescriptionSet = true
            flushPendingIce(peer)
            val answer = peer.connection.createAnswerSuspend(MediaConstraints())
            peer.connection.setLocalDescriptionSuspend(answer)
            signaling.send(buildJsonObject {
                put("kind", "answer")
                put("from", localUserId)
                put("to", from)
                put("sdp", answer.description)
            })
        }.onFailure { Log.w(TAG, "handleOffer from $from failed: ${it.message}") }
    }

    private suspend fun handleAnswer(from: String, sdp: String) {
        val peer = peers[from] ?: return
        runCatching {
            peer.connection.setRemoteDescriptionSuspend(SessionDescription(SessionDescription.Type.ANSWER, sdp))
            peer.remoteDescriptionSet = true
            flushPendingIce(peer)
        }.onFailure { Log.w(TAG, "handleAnswer from $from failed: ${it.message}") }
    }

    private fun handleIce(from: String, candidate: IceCandidate) {
        Log.d(TAG, "remote candidate from $from: ${candidate.sdp}")
        val peer = peers[from]
        if (peer == null || !peer.remoteDescriptionSet) {
            peers.getOrPut(from) { createPeer(from) }.pendingRemoteIce.add(candidate)
        } else {
            peer.connection.addIceCandidate(candidate)
        }
    }

    private fun flushPendingIce(peer: Peer) {
        peer.pendingRemoteIce.forEach { peer.connection.addIceCandidate(it) }
        peer.pendingRemoteIce.clear()
    }

    private fun broadcastVideoState(on: Boolean) {
        signaling.send(buildJsonObject {
            put("kind", "video-state")
            put("from", localUserId)
            put("on", on)
        })
    }

    private fun sendCurrentStateTo(remoteId: String) {
        signaling.send(buildJsonObject {
            put("kind", "video-state")
            put("from", localUserId)
            put("to", remoteId)
            put("on", cameraOnState)
        })
        signaling.send(buildJsonObject {
            put("kind", "hand-raised")
            put("from", localUserId)
            put("to", remoteId)
            put("raised", handRaisedState)
        })
    }
}

private suspend fun PeerConnection.createOfferSuspend(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) { cont.resume(sdp) }
            override fun onCreateFailure(error: String) { cont.resumeWithException(IllegalStateException(error)) }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, constraints)
    }

private suspend fun PeerConnection.createAnswerSuspend(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
        createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) { cont.resume(sdp) }
            override fun onCreateFailure(error: String) { cont.resumeWithException(IllegalStateException(error)) }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, constraints)
    }

private suspend fun PeerConnection.setLocalDescriptionSuspend(sdp: SessionDescription): Unit =
    suspendCancellableCoroutine { cont ->
        setLocalDescription(object : SdpObserver {
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(error: String) { cont.resumeWithException(IllegalStateException(error)) }
            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
        }, sdp)
    }

private suspend fun PeerConnection.setRemoteDescriptionSuspend(sdp: SessionDescription): Unit =
    suspendCancellableCoroutine { cont ->
        setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(error: String) { cont.resumeWithException(IllegalStateException(error)) }
            override fun onCreateSuccess(sdp: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
        }, sdp)
    }

private suspend fun PeerConnection.getStatsSuspend(): RTCStatsReport =
    suspendCancellableCoroutine { cont -> getStats { report -> cont.resume(report) } }
