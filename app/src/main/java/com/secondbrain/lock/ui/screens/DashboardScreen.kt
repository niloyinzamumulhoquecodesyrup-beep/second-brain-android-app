package com.secondbrain.lock.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.data.AppLimit
import com.secondbrain.lock.ui.screens.work.StreakSurface
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold400
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink700
import com.secondbrain.lock.ui.theme.Ink800
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.fullAuraBackground
import com.secondbrain.lock.util.drawableToImageBitmap

data class DashboardRow(
    val limit: AppLimit,
    val usedMillis: Long
)

/** Ported to the Streak Section redesign's visual language (see RewardPanel/TasksPanel in
 * ui/screens/work/): a StreakAccent eyebrow header, a tinted [StreakSurface] hero card, and
 * uniformly-tinted row entries instead of the old one-SbCard-per-app / all-Emerald layout. */
@Composable
fun DashboardScreen(
    rows: List<DashboardRow>,
    onAdd: () -> Unit,
    onRemove: (AppLimit) -> Unit,
    onToggleSchedule: (AppLimit, Boolean) -> Unit,
    onToggleFocus: (AppLimit, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    topBar: @Composable () -> Unit = {}
) {
    Scaffold(
        containerColor = Ink950,
        floatingActionButton = {
            // Shield's content fills the full window behind the glass top/bottom bars (same as
            // every other tab, for the haze blur to work), so this FAB — anchored by Scaffold to
            // the bottom of that full area — was ending up underneath the bottom nav bar instead
            // of floating above it. Lift it clear by the same amount the bar actually occupies.
            FloatingActionButton(
                onClick = onAdd,
                containerColor = StreakAccent,
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding())
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add app limit")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fullAuraBackground()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp
                )
        ) {
            topBar()
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Mist400)
                }
            }
            Spacer(Modifier.height(8.dp))

            StreakSurface {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SbSectionTitle("App limits", color = Mist300)
                    Text(
                        "${rows.count { it.usedMillis >= it.limit.dailyLimitMinutes * 60_000L }} locked · ${rows.size} monitored",
                        style = SecondBrainTypography.bodySmall,
                        color = Mist400
                    )
                }
                Spacer(Modifier.height(14.dp))

                if (rows.isEmpty()) {
                    Text(
                        "Nothing monitored yet. Tap + to pick an app and set a daily time budget.",
                        style = SecondBrainTypography.bodyMedium,
                        color = Mist400
                    )
                } else {
                    rows.forEachIndexed { index, row ->
                        AppLimitRow(
                            row = row,
                            onRemove = { onRemove(row.limit) },
                            onToggleSchedule = { onToggleSchedule(row.limit, it) },
                            onToggleFocus = { onToggleFocus(row.limit, it) }
                        )
                        if (index != rows.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }
            Spacer(Modifier.height(72.dp))
        }
    }
}

// Every row shares this one background rather than TasksPanel's rowBg cycle — that cycle's
// second color is StreakCard, identical to the StreakSurface container this list sits inside,
// so every other row visually disappeared into the card behind it (see screenshot feedback:
// Facebook read as "boxed", YouTube didn't, purely from which index it landed on).
//
// Read Ink800 directly at each use site below rather than caching it in a top-level val — Ink800
// is a `get()` property that re-evaluates SbThemeState.mode on every access, so hoisting it into
// a plain `val` here would freeze it at whatever theme was active the first time this file's
// properties got initialized, and it would never update again on a theme toggle (a real bug this
// file shipped with briefly: rows stayed light-mode white forever, even in dark mode).

@Composable
private fun AppLimitRow(
    row: DashboardRow,
    onRemove: () -> Unit,
    onToggleSchedule: (Boolean) -> Unit,
    onToggleFocus: (Boolean) -> Unit
) {
    val limitMillis = row.limit.dailyLimitMinutes * 60_000L
    val fraction = (row.usedMillis.toFloat() / limitMillis.toFloat()).coerceIn(0f, 1f)
    val locked = row.usedMillis >= limitMillis
    val accent = when {
        locked -> StreakAccent
        fraction >= 0.9f -> Gold400
        else -> Emerald400
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink800)
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AppIcon(row.limit.packageName, size = 36.dp)
                // Traffic-light status badge (matches TasksPanel/RoutineRow's colored-dot idiom)
                // so the real app icon can stand in for the name without losing the at-a-glance
                // safe/near-limit/locked read the old text label gave.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .border(BorderStroke(2.dp, Ink800), CircleShape)
                )
            }
            Spacer(Modifier.width(12.dp))
            if (!locked) {
                Column(Modifier.weight(1f)) {
                    Text(row.limit.appName, style = SecondBrainTypography.bodyMedium, color = Mist100)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${row.usedMillis / 60_000} of ${row.limit.dailyLimitMinutes} min today",
                        style = SecondBrainTypography.bodySmall,
                        color = Mist300
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Remove ${row.limit.appName}", tint = Mist400)
            }
        }
        row.limit.openCountLimit?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "Capped at $it opens/day",
                style = SecondBrainTypography.bodySmall,
                color = Mist400
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Ink700)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
        }
        Spacer(Modifier.height(12.dp))
        ToggleRow("Block during schedule windows", row.limit.blockDuringSchedule, onToggleSchedule)
        Spacer(Modifier.height(10.dp))
        ToggleRow("Block during focus sessions", row.limit.blockDuringFocus, onToggleFocus)
    }
}

/** Mirrors AddAppScreen's icon treatment — falls back to a generic glyph if the app was uninstalled
 * after the limit was created (getApplicationIcon throws NameNotFoundException in that case). */
@Composable
private fun AppIcon(packageName: String, size: Dp = 36.dp) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }
            .getOrNull()
            ?.let { drawableToImageBitmap(it) }
    }
    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(Ink600),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Apps,
                contentDescription = null,
                tint = Mist400,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = SecondBrainTypography.bodySmall, color = Mist300, modifier = Modifier.weight(1f))
        // No explicit height here (a prior 24.dp constraint was silently ignored — M3's Switch
        // enforces its own ~48dp minimum touch target internally regardless of an external size
        // modifier — so the two switches were painting past their allotted row bounds and
        // visually overlapping the row below despite the spacer between them).
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = StreakAccent, checkedThumbColor = Color.White)
        )
    }
}
