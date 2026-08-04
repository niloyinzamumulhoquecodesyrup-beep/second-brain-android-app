package com.secondbrain.lock.ui.screens.mindverse

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.data.repo.MindverseRepository
import com.secondbrain.lock.network.dto.MindcordDomain
import com.secondbrain.lock.ui.screens.work.StreakSurface
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import kotlinx.coroutines.launch

/**
 * Ports pages/other-brains.js's Mindcord sub-tab: pick/join a domain room, then hand off to
 * [MindverseRoomScreen] — a full-screen call takeover pushed as its own nav destination —
 * for everything that happens once inside. Deliberately dropped vs. the web: the Phase 2
 * WebRTC voice mesh and file uploads (mindcord/upload.js) — both need infra (WebRTC signaling,
 * multipart handling) well beyond this pass's scope; the camera grid/chat/people room UI works
 * end-to-end against the real API, but tiles show identity chips rather than live video.
 */
@Composable
fun MindcordTab(onOpenRoom: () -> Unit) {
    val domains by MindverseRepository.domains.collectAsState()
    val currentRoom by MindverseRepository.currentRoom.collectAsState()

    LaunchedEffect(Unit) { MindverseRepository.refreshDomains() }
    // Reached both right after RoomRow's onJoin succeeds and when this tab is revisited while
    // already joined (e.g. coming back from another tab) — either way, currentRoom flipping
    // non-null means the full-screen room takes over.
    LaunchedEffect(currentRoom?.roomId) { if (currentRoom != null) onOpenRoom() }

    if (currentRoom == null) {
        RoomPicker(domains)
    }
}

@Composable
private fun RoomPicker(domains: List<MindcordDomain>) {
    val scope = rememberCoroutineScope()

    StreakSurface {
        SbLabel("Join a room", color = StreakAccent)
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
                color = if (live > 0) StreakAccent else Mist400,
                style = SecondBrainTypography.bodySmall
            )
        }
        TextButton(onClick = onJoin) { Text("Join", color = StreakAccent) }
    }
}
