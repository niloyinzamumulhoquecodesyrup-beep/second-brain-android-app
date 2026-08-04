package com.secondbrain.lock.ui.screens.mindverse

import android.Manifest
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.secondbrain.lock.data.repo.MindverseRepository
import com.secondbrain.lock.network.dto.MindcordMessage
import com.secondbrain.lock.network.dto.MindcordParticipant
import com.secondbrain.lock.network.dto.OtherBrainsIdentity
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.util.Permissions
import com.secondbrain.lock.webrtc.MindcordCallManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

// Forced-dark "Ink" call surface — deliberately hardcoded rather than the adaptive Ink9xx/
// Rose400/Gold500 theme tokens (which flip in light mode): a video-call-style takeover reads
// on camera tiles only if it stays dark regardless of the user's app-wide theme choice, same
// as Zoom/Meet/Discord's call screens ignore OS light mode.
private val RoomBg = Color(0xFF08090B)
private val RoomTile = Color(0xFF1B282C)
private val RoomTileBorder = Color(0xFF39474D)
private val RoomChip = Color(0xFF24343A)
private val RoomSpeakingTile = Color(0xFF1C2A2E)
private val RoomSheetBg = Color(0xFF121B1E)
private val RoomOverflowBorder = Color(0xFF455358)
private val RoomTextPrimary = Color(0xFFF4F5F7)
private val RoomTextSecondary = Color(0xA8F4F5F7)
private val RoomTextTertiary = Color(0x6BF4F5F7)
private val RoomTextBody = Color(0xDBF4F5F7)
private val RoomMuted = Color(0xFFFB7185)
private val RoomHandRaised = Color(0xFFE0C07E)

private val TileHeight = 150.dp
private const val MAX_OTHER_TILES = 5

/**
 * The Mindcord "in-room" screen: camera grid + call controls, with chat and the participant
 * roster living in one sheet reachable either from the header's chat icon or the grid's
 * overflow tile. Pushed as its own nav destination (see AppNavHost's MINDVERSE_ROOM_ROUTE) so
 * it can fully hide the bottom nav bar, matching a real call takeover.
 *
 * Voice/video is a real WebRTC mesh ([MindcordCallManager]) signaled over a Supabase Realtime
 * broadcast channel — one [org.webrtc.PeerConnection] per other participant, STUN-only. Camera/
 * mic permission is requested once on entering the room; denying either just means that track
 * never gets created for this session (rejoin after granting to pick it up — there's no
 * mid-call "grant permission, renegotiate" path here, see the manager's doc comment). Other
 * participants' mic-mute state still isn't shown (no signal carries it), but camera on/off now
 * is, via an explicit "video-state" broadcast — a disabled/uncaptured track just stops producing
 * frames, it doesn't tell the remote side *why*, so the tile fallback-to-avatar logic can't
 * infer it from the media alone.
 */
@Composable
fun MindverseRoomScreen(onBack: () -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    val room by MindverseRepository.currentRoom.collectAsState()
    val you by MindverseRepository.identity.collectAsState()
    val participants by MindverseRepository.roomParticipants.collectAsState()
    val messages by MindverseRepository.roomMessages.collectAsState()
    val error by MindverseRepository.error.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentRoom = room
    // Room left/kicked/expired from under us (e.g. another client called leaveRoom) — bail
    // back to the picker rather than showing a dead screen.
    LaunchedEffect(currentRoom) { if (currentRoom == null) onBack() }
    if (currentRoom == null) return

    // New messages/participant changes push in live over Supabase Realtime (see
    // MindcordRealtime); this is just the initial snapshot plus a slow fallback poll for
    // whatever the socket misses (a dropped connection, a change that raced the subscribe).
    LaunchedEffect(currentRoom.roomId) {
        MindverseRepository.refreshRoomMessages()
        MindverseRepository.refreshRoomParticipants()
        MindverseRepository.connectRealtime(currentRoom.roomId)
        try {
            while (isActive) {
                delay(15_000)
                MindverseRepository.refreshRoomMessages()
                MindverseRepository.refreshRoomParticipants()
            }
        } finally {
            MindverseRepository.disconnectRealtime()
        }
    }
    // Mirrors web's MindcordSection.js 20s heartbeat: without it the server expires this
    // participant's last_seen_at after 45s and silently drops them from the live roster.
    LaunchedEffect(currentRoom.roomId) {
        while (isActive) {
            delay(20_000)
            MindverseRepository.sendHeartbeat()
        }
    }

    var micMuted by remember { mutableStateOf(true) }
    var cameraOn by remember { mutableStateOf(false) }
    var handRaised by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<RoomSheetTab?>(null) }
    var lastReadCount by remember { mutableStateOf(0) }

    val others = remember(participants, you?.userId) {
        participants.filter { it.userId != you?.userId }
    }

    val callManager = remember(currentRoom.roomId) { MindcordCallManager(context, you?.userId.orEmpty()) }
    DisposableEffect(callManager) { onDispose { callManager.close() } }

    val requestCallPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        callManager.start(
            currentRoom.roomId,
            hasMicPermission = results[Manifest.permission.RECORD_AUDIO] == true,
            hasCameraPermission = results[Manifest.permission.CAMERA] == true
        )
    }
    // Waits for `you` — the deterministic offerer comparison needs a real user_id, and identity
    // is only ever null for a flash right after the identity gate (MindverseScreen) passes.
    LaunchedEffect(currentRoom.roomId, you?.userId) {
        if (you?.userId.isNullOrBlank()) return@LaunchedEffect
        requestCallPermissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
    }
    LaunchedEffect(others) { callManager.syncPeers(others.map { it.userId }) }

    val localVideoTrack by callManager.localVideoTrackFlow.collectAsState()
    val remoteVideoTracks by callManager.remoteVideoTracks.collectAsState()
    val remoteVideoOn by callManager.remoteVideoOn.collectAsState()
    val remoteHandRaised by callManager.remoteHandRaised.collectAsState()
    val localSpeaking by callManager.localSpeaking.collectAsState()
    val remoteSpeaking by callManager.remoteSpeaking.collectAsState()

    // Deliberately doesn't call onBack() directly — leaveRoom() clears currentRoom before its
    // network round-trip (see its doc comment), and the LaunchedEffect above reacts to that by
    // popping. Routing every exit path through that one reactive spot avoids a race where a
    // direct onBack() here pops back to the room picker a beat before currentRoom is actually
    // null, so its own "already in a room" effect immediately re-navigates back in.
    val leave: () -> Unit = { scope.launch { MindverseRepository.leaveRoom() } }

    // The sheet handles its own back-press dismissal; only take over when it's closed.
    BackHandler(enabled = sheet == null) { leave() }

    Box(Modifier.fillMaxSize().background(RoomBg)) {
        Column(Modifier.fillMaxSize()) {
            RoomHeader(
                domain = currentRoom.domain,
                inRoomCount = others.size + 1,
                unreadCount = (messages.size - lastReadCount).coerceAtLeast(0),
                topInset = contentPadding.calculateTopPadding(),
                onBack = leave,
                onOpenChat = { sheet = RoomSheetTab.CHAT }
            )
            RoomGrid(
                you = you,
                micMuted = micMuted,
                handRaised = handRaised,
                others = others,
                localVideoTrack = localVideoTrack,
                cameraOn = cameraOn,
                localSpeaking = localSpeaking,
                remoteVideoTracks = remoteVideoTracks,
                remoteVideoOn = remoteVideoOn,
                remoteHandRaised = remoteHandRaised,
                remoteSpeaking = remoteSpeaking,
                eglContext = callManager.eglBase.eglBaseContext,
                onShowAll = { sheet = RoomSheetTab.PEOPLE },
                modifier = Modifier.weight(1f)
            )
            RoomControlBar(
                cameraOn = cameraOn,
                micMuted = micMuted,
                handRaised = handRaised,
                bottomInset = contentPadding.calculateBottomPadding(),
                onToggleCamera = {
                    if (Permissions.hasCamera(context)) {
                        val next = !cameraOn
                        cameraOn = next
                        callManager.setCameraEnabled(next)
                    } else {
                        Toast.makeText(context, "Camera permission needed — rejoin the room after granting it", Toast.LENGTH_LONG).show()
                    }
                },
                onToggleMic = {
                    if (Permissions.hasMicrophone(context)) {
                        val next = !micMuted
                        micMuted = next
                        callManager.setMicEnabled(!next)
                    } else {
                        Toast.makeText(context, "Microphone permission needed — rejoin the room after granting it", Toast.LENGTH_LONG).show()
                    }
                },
                onScreenShare = {
                    Toast.makeText(context, "Screen sharing isn't available yet", Toast.LENGTH_SHORT).show()
                },
                onToggleHand = {
                    val next = !handRaised
                    handRaised = next
                    callManager.setHandRaised(next)
                },
                onInvite = { shareRoomInvite(context, currentRoom.domain) },
                onLeave = leave
            )
        }
    }

    val activeSheet = sheet
    if (activeSheet != null) {
        RoomSheet(
            initialTab = activeSheet,
            you = you,
            participants = others,
            messages = messages,
            error = error,
            onSend = { body -> scope.launch { MindverseRepository.sendRoomMessage(body) } },
            onInvite = { shareRoomInvite(context, currentRoom.domain) },
            onChatViewed = { count -> lastReadCount = count },
            onDismiss = { sheet = null }
        )
    }
}

private fun shareRoomInvite(context: Context, domain: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Join me in the $domain room on Mindverse — open Slay Task and tap Mindverse to jump in.")
    }
    context.startActivity(Intent.createChooser(intent, "Invite to room"))
}

@Composable
private fun RoomHeader(
    domain: String,
    inRoomCount: Int,
    unreadCount: Int,
    topInset: Dp,
    onBack: () -> Unit,
    onOpenChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topInset + 10.dp, start = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoomIconChip(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Leave room", tint = RoomTextPrimary)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                domain,
                color = RoomTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(StreakAccent))
                Spacer(Modifier.width(7.dp))
                Text("Mindverse · $inRoomCount in room", color = RoomTextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Box {
            RoomIconChip(onClick = onOpenChat) {
                Icon(Icons.Filled.ChatBubble, contentDescription = "Chat", tint = RoomTextPrimary)
            }
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(StreakAccent)
                        .border(2.dp, RoomBg, CircleShape)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (unreadCount > 9) "9+" else "$unreadCount",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomIconChip(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(RoomChip)
            .border(1.dp, RoomTileBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun RoomAvatar(avatarKey: String, size: Dp, highlighted: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (highlighted) StreakAccent else RoomChip),
        contentAlignment = Alignment.Center
    ) {
        Text(
            avatarKey.ifBlank { "🙂" },
            fontSize = (size.value * 0.4).sp,
            color = if (highlighted) Color.White else RoomTextPrimary
        )
    }
}

/** Staggered pulsing bars — the "someone's mic is live" indicator, matching the design's
 * per-bar animation-delay waveform without needing the CSS keyframes it was built from. */
@Composable
private fun Waveform(color: Color, maxHeight: Dp = 12.dp, barCount: Int = 4) {
    val infinite = rememberInfiniteTransition(label = "waveform")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        repeat(barCount) { i ->
            val fraction by infinite.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(620 + i * 90, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 90)
                ),
                label = "bar$i"
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height(maxHeight * fraction)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun ParticipantTile(
    avatarKey: String,
    name: String,
    speaking: Boolean = false,
    muted: Boolean? = null,
    handRaised: Boolean = false,
    videoTrack: VideoTrack? = null,
    eglContext: EglBase.Context? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TileHeight)
            .clip(shape)
            .background(if (speaking) RoomSpeakingTile else RoomTile)
            .border(if (speaking) 1.5.dp else 1.dp, if (speaking) StreakAccent else RoomTileBorder, shape)
    ) {
        if (videoTrack != null && eglContext != null) {
            VideoSurface(videoTrack, eglContext, modifier = Modifier.fillMaxSize())
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(RoomBg.copy(alpha = 0.78f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (muted == true) {
                    Icon(
                        Icons.Filled.MicOff,
                        contentDescription = null,
                        tint = RoomMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(name, color = RoomTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RoomAvatar(avatarKey, 60.dp, highlighted = speaking)
                Spacer(Modifier.height(11.dp))
                if (speaking) {
                    Text(name, color = RoomTextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(7.dp))
                    Waveform(StreakAccent)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (muted == true) {
                            Icon(
                                Icons.Filled.MicOff,
                                contentDescription = null,
                                tint = RoomMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            name,
                            color = if (muted == true) RoomTextSecondary else RoomTextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        if (handRaised) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(9.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(RoomHandRaised.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PanTool,
                    contentDescription = "Hand raised",
                    tint = RoomHandRaised,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/** One [SurfaceViewRenderer] per tile, kept alive for this composable's lifetime via `remember`
 * so re-attaching the video sink on every recomposition doesn't tear down/recreate the native
 * view (that would flash black on every frame the track/tile state changes). */
@Composable
private fun VideoSurface(track: VideoTrack, eglContext: EglBase.Context, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val renderer = remember { SurfaceViewRenderer(context) }

    DisposableEffect(renderer) {
        renderer.init(eglContext, null)
        renderer.setEnableHardwareScaler(true)
        onDispose { renderer.release() }
    }
    DisposableEffect(renderer, track) {
        track.addSink(renderer)
        onDispose { track.removeSink(renderer) }
    }
    AndroidView(modifier = modifier, factory = { renderer })
}

private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.dp): Modifier =
    this.drawWithContent {
        drawContent()
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(width = strokeWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        )
    }

@Composable
private fun OverflowTile(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TileHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(RoomTile)
            .dashedBorder(RoomOverflowBorder, 16.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+$count", color = StreakAccent, fontSize = 28.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "SHOW ALL",
                color = RoomTextTertiary,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RoomGrid(
    you: OtherBrainsIdentity?,
    micMuted: Boolean,
    handRaised: Boolean,
    others: List<MindcordParticipant>,
    localVideoTrack: VideoTrack?,
    cameraOn: Boolean,
    localSpeaking: Boolean,
    remoteVideoTracks: Map<String, VideoTrack>,
    remoteVideoOn: Map<String, Boolean>,
    remoteHandRaised: Map<String, Boolean>,
    remoteSpeaking: Map<String, Boolean>,
    eglContext: EglBase.Context,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = others.take(MAX_OTHER_TILES)
    val overflow = (others.size - MAX_OTHER_TILES).coerceAtLeast(0)

    // A plain (non-lazy) grid, not LazyVerticalGrid — confirmed on a real device: the video
    // tiles render into a SurfaceViewRenderer, whose hardware-compositor "hole punch" doesn't
    // track Compose's lazy virtualization/scroll offsetting correctly, so a live camera feed
    // rendered fine on the emulator's software display but never actually appeared on real
    // hardware inside a LazyVerticalGrid. The roster here is always small and bounded
    // (MAX_OTHER_TILES + you + one overflow tile, so ≤7 total), so lazy virtualization was
    // never buying anything — dropping it sidesteps the bug instead of working around it.
    val tiles = buildList<Pair<Any, @Composable () -> Unit>> {
        add("you" to @Composable {
            ParticipantTile(
                avatarKey = you?.avatarKey.orEmpty(),
                name = "You",
                speaking = !micMuted && localSpeaking,
                muted = micMuted,
                handRaised = handRaised,
                videoTrack = if (cameraOn) localVideoTrack else null,
                eglContext = eglContext
            )
        })
        visible.forEach { p ->
            add((p.userId.ifBlank { p.displayName }) to @Composable {
                val hasVideo = remoteVideoOn[p.userId] == true
                ParticipantTile(
                    avatarKey = p.avatarKey,
                    name = p.displayName.ifBlank { "Someone" },
                    speaking = remoteSpeaking[p.userId] == true,
                    handRaised = remoteHandRaised[p.userId] == true,
                    videoTrack = if (hasVideo) remoteVideoTracks[p.userId] else null,
                    eglContext = eglContext
                )
            })
        }
        if (overflow > 0) {
            add("overflow" to @Composable { OverflowTile(overflow, onShowAll) })
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTiles.forEach { (tileKey, content) ->
                    key(tileKey) { Box(Modifier.weight(1f)) { content() } }
                }
                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RoomControlBar(
    cameraOn: Boolean,
    micMuted: Boolean,
    handRaised: Boolean,
    bottomInset: Dp,
    onToggleCamera: () -> Unit,
    onToggleMic: () -> Unit,
    onScreenShare: () -> Unit,
    onToggleHand: () -> Unit,
    onInvite: () -> Unit,
    onLeave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RoomSheetBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .padding(bottom = bottomInset),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlButton(
            icon = if (cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
            tint = RoomTextPrimary,
            onClick = onToggleCamera
        )
        ControlButton(
            icon = if (micMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
            bg = if (micMuted) RoomMuted.copy(alpha = 0.18f) else RoomChip,
            tint = if (micMuted) RoomMuted else RoomTextPrimary,
            onClick = onToggleMic
        )
        ControlButton(icon = Icons.AutoMirrored.Filled.ScreenShare, tint = RoomTextPrimary, onClick = onScreenShare)
        ControlButton(
            icon = Icons.Filled.PanTool,
            bg = if (handRaised) RoomHandRaised.copy(alpha = 0.18f) else RoomChip,
            tint = if (handRaised) RoomHandRaised else RoomTextPrimary,
            onClick = onToggleHand
        )
        ControlButton(icon = Icons.Filled.PersonAdd, tint = RoomTextPrimary, onClick = onInvite)
        Box(
            modifier = Modifier
                .size(width = 62.dp, height = 50.dp)
                .clip(RoundedCornerShape(50))
                .background(StreakAccent)
                .clickable(onClick = onLeave),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = "Leave call", tint = Color.White)
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, tint: Color, bg: Color = RoomChip, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = tint) }
}

private enum class RoomSheetTab { CHAT, PEOPLE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomSheet(
    initialTab: RoomSheetTab,
    you: OtherBrainsIdentity?,
    participants: List<MindcordParticipant>,
    messages: List<MindcordMessage>,
    error: String?,
    onSend: (String) -> Unit,
    onInvite: () -> Unit,
    onChatViewed: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember(initialTab) { mutableStateOf(initialTab) }

    // Marks messages read continuously while the Chat tab is the one showing, not just once
    // on open — so a message arriving while you're already looking at it doesn't count as
    // unread the next time you close and reopen the sheet.
    LaunchedEffect(tab, messages.size) {
        if (tab == RoomSheetTab.CHAT) onChatViewed(messages.size)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = RoomSheetBg, contentColor = RoomTextPrimary) {
        // ModalBottomSheet's own default windowInsets don't reliably keep this sheet's bottom
        // content (the chat composer) clear of the 3-button nav bar / gesture inset on every
        // device — navigationBarsPadding() here is belt-and-braces so the composer and its
        // send button never sit under system chrome.
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetTabPill("Chat", tab == RoomSheetTab.CHAT) { tab = RoomSheetTab.CHAT }
                Spacer(Modifier.width(8.dp))
                SheetTabPill("People · ${participants.size + 1}", tab == RoomSheetTab.PEOPLE) { tab = RoomSheetTab.PEOPLE }
                Spacer(Modifier.weight(1f))
                RoomIconChip(onClick = onInvite) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Invite", tint = RoomTextPrimary)
                }
            }
            Spacer(Modifier.height(6.dp))
            when (tab) {
                RoomSheetTab.CHAT -> ChatSheetBody(messages, error, onSend)
                RoomSheetTab.PEOPLE -> PeopleSheetBody(you, participants, onInvite)
            }
        }
    }
}

@Composable
private fun SheetTabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) RoomTextPrimary else RoomChip)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (selected) RoomBg else RoomTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun ChatSheetBody(messages: List<MindcordMessage>, error: String?, onSend: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (messages.isEmpty()) {
                Text(
                    "No messages yet — say hello.",
                    color = RoomTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                messages.forEach { m -> RoomChatRow(m) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatComposer(placeholder = "Message the room", maxLength = 500, showCounter = true, onSend = onSend)
        }
        ErrorLine(error)
    }
}

@Composable
private fun RoomChatRow(m: MindcordMessage) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        RoomAvatar(m.avatarKey, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(m.displayName.ifBlank { "Someone" }, color = RoomTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(relativeTime(m.createdAt), color = RoomTextTertiary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(m.body, color = RoomTextBody, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun PeopleSheetBody(you: OtherBrainsIdentity?, participants: List<MindcordParticipant>, onInvite: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp, max = 460.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "IN ROOM — ${participants.size + 1}",
            color = RoomTextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        PersonRow(you?.avatarKey.orEmpty(), "You", highlight = true)
        participants.forEach { p -> PersonRow(p.avatarKey, p.displayName.ifBlank { "Someone" }) }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(50))
                .background(RoomTextPrimary)
                .clickable(onClick = onInvite),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = RoomBg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text("Invite people", color = RoomBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PersonRow(avatarKey: String, name: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) RoomSpeakingTile else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoomAvatar(avatarKey, 38.dp)
        Spacer(Modifier.width(12.dp))
        Text(name, color = RoomTextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (highlight) {
            Text("YOU", color = StreakAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}
