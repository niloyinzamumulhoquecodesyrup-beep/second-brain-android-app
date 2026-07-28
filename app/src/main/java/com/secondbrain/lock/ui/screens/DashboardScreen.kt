package com.secondbrain.lock.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.data.AppLimit
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold400
import com.secondbrain.lock.ui.theme.GradientText
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.fullAuraBackground
import androidx.compose.ui.unit.sp

data class DashboardRow(
    val limit: AppLimit,
    val usedMillis: Long
)

@Composable
fun DashboardScreen(
    rows: List<DashboardRow>,
    onAdd: () -> Unit,
    onRemove: (AppLimit) -> Unit,
    onToggleSchedule: (AppLimit, Boolean) -> Unit,
    onToggleFocus: (AppLimit, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
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
                containerColor = Emerald400,
                contentColor = Ink950,
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
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = contentPadding.calculateTopPadding() + 28.dp,
                    bottom = 28.dp
                )
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SbLabel("Overview")
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Mist400)
                }
            }
            Spacer(Modifier.height(8.dp))
            GradientText("Your limits", fontSize = 40.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "${rows.count { it.usedMillis >= it.limit.dailyLimitMinutes * 60_000L }} locked · ${rows.size} monitored",
                style = SecondBrainTypography.bodySmall,
                color = Mist400
            )
            Spacer(Modifier.height(24.dp))

            if (rows.isEmpty()) {
                SbCard {
                    Text(
                        "Nothing monitored yet. Tap + to pick an app and set a daily time budget.",
                        style = SecondBrainTypography.bodyMedium,
                        color = Mist400
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(rows, key = { it.limit.packageName }) { row ->
                        AppLimitCard(
                            row,
                            onRemove = { onRemove(row.limit) },
                            onToggleSchedule = { onToggleSchedule(row.limit, it) },
                            onToggleFocus = { onToggleFocus(row.limit, it) }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AppLimitCard(
    row: DashboardRow,
    onRemove: () -> Unit,
    onToggleSchedule: (Boolean) -> Unit,
    onToggleFocus: (Boolean) -> Unit
) {
    val limitMillis = row.limit.dailyLimitMinutes * 60_000L
    val fraction = (row.usedMillis.toFloat() / limitMillis.toFloat()).coerceIn(0f, 1f)
    val locked = row.usedMillis >= limitMillis
    val accent = when {
        locked -> Rose400
        fraction >= 0.9f -> Gold400
        else -> Emerald400
    }

    SbCard(topBorderColor = accent) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(row.limit.appName, style = SecondBrainTypography.titleMedium, color = Color.White)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (locked) "Locked until midnight" else "${row.usedMillis / 60_000} of ${row.limit.dailyLimitMinutes} min today",
                    style = SecondBrainTypography.bodySmall,
                    color = if (locked) Rose400 else Mist300
                )
                row.limit.openCountLimit?.let {
                    Text(
                        "Capped at $it opens/day",
                        style = SecondBrainTypography.bodySmall,
                        color = Mist400
                    )
                }
            }
            Text(
                "×",
                style = SecondBrainTypography.titleMedium,
                color = Mist400,
                modifier = Modifier.clickable(onClick = onRemove).padding(4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Ink600)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
            )
        }
        Spacer(Modifier.height(14.dp))
        ToggleRow("Block during schedule windows", row.limit.blockDuringSchedule, onToggleSchedule)
        Spacer(Modifier.height(6.dp))
        ToggleRow("Block during focus sessions", row.limit.blockDuringFocus, onToggleFocus)
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Emerald400, checkedThumbColor = Ink950),
            modifier = Modifier.height(24.dp)
        )
    }
}
