package com.secondbrain.lock.ui.screens.mindverse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.data.repo.MindverseRepository
import com.secondbrain.lock.network.dto.JoinRoomResponse
import com.secondbrain.lock.network.dto.MindcordDomain
import com.secondbrain.lock.network.dto.MindcordParticipant
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.Violet400
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ports pages/other-brains.js's Mindcord sub-tab as a real, working text chat: pick/join a
 * domain room, then poll messages + the participant roster while inside it. Deliberately
 * dropped vs. the web: the Phase 2 WebRTC voice mesh and file uploads (mindcord/upload.js) —
 * both need infra (WebRTC signaling, multipart handling) well beyond this pass's scope; text
 * chat is the actual point of the feature and works end-to-end against the real API.
 */
@Composable
fun MindcordTab() {
    val domains by MindverseRepository.domains.collectAsState()
    val currentRoom by MindverseRepository.currentRoom.collectAsState()

    LaunchedEffect(Unit) { MindverseRepository.refreshDomains() }

    val room = currentRoom
    if (room == null) {
        RoomPicker(domains)
    } else {
        MindcordRoomView(room)
    }
}

@Composable
private fun RoomPicker(domains: List<MindcordDomain>) {
    val scope = rememberCoroutineScope()

    SbCard(topBorderColor = Emerald400) {
        SbLabel("Join a room", color = Emerald400)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick a domain to drop into a live text chat with whoever else is studying it right now.",
            color = Mist400,
            style = SecondBrainTypography.bodySmall
        )
        if (domains.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            domains.sortedWith(compareByDescending<MindcordDomain> { it.live?.count ?: 0 }.thenByDescending { it.brains })
                .forEach { domain ->
                    RoomRow(domain, onJoin = { scope.launch { MindverseRepository.joinRoom(domain.domain) } })
                }
        }
    }
}

@Composable
private fun RoomRow(domain: MindcordDomain, onJoin: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(domain.domain, color = Mist100, style = SecondBrainTypography.bodyMedium)
            val live = domain.live?.count ?: 0
            Text(
                if (live > 0) "$live here now" else "${domain.brains} brains study this",
                color = if (live > 0) Emerald400 else Mist400,
                style = SecondBrainTypography.bodySmall
            )
        }
        TextButton(onClick = onJoin) { Text("Join", color = Emerald400) }
    }
}

@Composable
private fun MindcordRoomView(room: JoinRoomResponse) {
    val messages by MindverseRepository.roomMessages.collectAsState()
    val participants by MindverseRepository.roomParticipants.collectAsState()
    val error by MindverseRepository.error.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(room.roomId) {
        while (isActive) {
            MindverseRepository.refreshRoomMessages()
            MindverseRepository.refreshRoomParticipants()
            delay(4000)
        }
    }

    // Mirrors web's MindcordSection.js 20s heartbeat: without it the server expires this
    // participant's last_seen_at after 45s and silently drops them from the live roster.
    LaunchedEffect(room.roomId) {
        while (isActive) {
            delay(20_000)
            MindverseRepository.sendHeartbeat()
        }
    }

    SbCard(topBorderColor = Violet400) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SbLabel(room.domain, color = Violet400, modifier = Modifier.weight(1f))
            TextButton(onClick = { scope.launch { MindverseRepository.leaveRoom() } }) {
                Text("Leave", color = Rose400, style = SecondBrainTypography.bodySmall)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("${participants.size} here now", color = Mist400, style = SecondBrainTypography.bodySmall)
        if (participants.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ParticipantChips(participants)
        }
        Spacer(Modifier.height(10.dp))

        if (messages.isEmpty()) {
            Text("No messages yet — say hello.", color = Mist400, style = SecondBrainTypography.bodySmall)
        } else {
            messages.forEach { m -> CommunityRow(m.avatarKey, m.displayName, m.createdAt, m.body) }
        }
        Spacer(Modifier.height(10.dp))
        ChatComposer(placeholder = "Message this room", maxLength = 500, showCounter = true) { body ->
            scope.launch { MindverseRepository.sendRoomMessage(body) }
        }
        ErrorLine(error)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParticipantChips(participants: List<MindcordParticipant>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        participants.forEach { p ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Ink500.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(p.avatarKey.ifBlank { "🙂" }, style = SecondBrainTypography.bodySmall)
                Spacer(Modifier.width(4.dp))
                Text(p.displayName, color = Mist300, style = SecondBrainTypography.bodySmall)
            }
        }
    }
}
