package com.secondbrain.lock.ui.screens.work

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.network.dto.Stats
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SecondBrainTypography

/**
 * P10's collapsed presentation of [RewardPanel] — a single 48dp row ("🔥 5 days · Lv 4 · tap for
 * stats") instead of the full score card, so WorkScreen's first screenful is [TasksPanel], not a
 * number. Tapping opens the exact same [StreakDetailScreen] [RewardPanel] already opens.
 *
 * Deliberately reuses [RewardMath.from] rather than deriving the streak/level a second way, so
 * this and the full detail screen can never disagree — RewardMath itself is NOT touched, it's
 * shared verbatim with the web app.
 */
@Composable
fun StreakStrip(stats: Stats?, onOpenDetail: () -> Unit) {
    if (stats == null) {
        // Matches RewardPanel's own null handling — local-first cold start means this is a rare
        // first-ever-launch case, not a normal loading flicker, but still worth a stable-height
        // placeholder over a layout jump when stats arrives.
        StreakSurface {
            Text("Loading…", color = Mist400, style = SecondBrainTypography.bodyMedium)
        }
        return
    }
    val computed = remember(stats) { RewardMath.from(stats) }
    val level = computed.gauges.first { it.label == "Streak" }.level

    StreakSurface(onClick = onOpenDetail) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("🔥 ${computed.streak} days", color = Mist300, style = SecondBrainTypography.bodyMedium)
            Text("·", color = Mist300, style = SecondBrainTypography.bodyMedium)
            Text("Lv $level", color = Mist300, style = SecondBrainTypography.bodyMedium)
            Text("·", color = Mist300, style = SecondBrainTypography.bodyMedium)
            Text("tap for stats", color = Mist300, style = SecondBrainTypography.bodyMedium)
        }
    }
}
