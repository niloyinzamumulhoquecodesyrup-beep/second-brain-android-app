package com.secondbrain.lock.ui.screens.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.network.dto.Packet
import com.secondbrain.lock.ui.screens.work.StreakSurface
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink700
import com.secondbrain.lock.ui.theme.Ink800
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.SbIconChip
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.StreakCard

/**
 * Packets (distilled excerpts saved via a note's "save as packet") had no place to be browsed —
 * they could be created but never listed anywhere in the app. Restyled to match the Work tab's
 * actual current components, mirroring `ParaCubeView.kt`'s own migration off `SbCard`: the flat,
 * borderless `StreakSurface` card (not `SbCard`'s tinted/bordered one) with a `StreakAccent`
 * header, plus the dot-and-line timeline row (`ui/screens/work/TasksPanel.kt`'s `TimelineRow`/
 * `rowBg`) instead of the older bordered/alpha tile grid GraduatedSection still uses. Tapping a
 * row jumps back to the note it was distilled from.
 */
@Composable
fun PacketsSection(packets: List<Packet>, onOpenNote: (String) -> Unit) {
    if (packets.isEmpty()) return
    StreakSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SbIconChip("📦", StreakAccent)
            Spacer(Modifier.width(10.dp))
            SbSectionTitle("Packets", color = StreakAccent)
        }
        Spacer(Modifier.height(14.dp))
        packets.forEachIndexed { index, packet ->
            PacketRow(
                packet = packet,
                bg = packetRowBg(index),
                showLineAbove = index != 0,
                showLineBelow = index != packets.lastIndex,
                onClick = { onOpenNote(packet.noteId) }
            )
        }
    }
}

/** Matches `rowBg()` from `ui/screens/work/TasksPanel.kt` — the same 4-color cycle that keeps
 * consecutive rows visually distinct without borders. */
private fun packetRowBg(index: Int): Color = listOf(Ink700, StreakCard, Ink800, Ink600)[index % 4]

/** Same dot-and-line-then-pill structure as `TasksPanel.kt`'s `TimelineRow`: a fixed-width line
 * column so consecutive rows' strokes read as one continuous line through every dot, then a
 * rounded content pill. Packets have no "now/next" concept, so the dot is a flat StreakAccent
 * (matching `ParaCubeView`'s own pager-dot/section-title accent) rather than switching color the
 * way a task/routine row's does. */
private val PACKET_DOT_OFFSET = 24.dp

@Composable
private fun PacketRow(packet: Packet, bg: Color, showLineAbove: Boolean, showLineBelow: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.width(18.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showLineAbove) {
                Box(Modifier.width(2.dp).height(PACKET_DOT_OFFSET).background(Ink600))
            } else {
                Spacer(Modifier.height(PACKET_DOT_OFFSET))
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(StreakAccent))
            if (showLineBelow) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.width(2.dp).weight(1f).background(Ink600))
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Gold500.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Text("📦", fontSize = 14.sp) }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    packet.title,
                    color = Mist100,
                    style = SecondBrainTypography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                packet.content?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Mist300, style = SecondBrainTypography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(Modifier.width(8.dp))
            Text("›", color = Mist300, fontSize = 18.sp)
        }
    }
}
