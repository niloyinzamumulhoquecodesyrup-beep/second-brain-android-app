package com.secondbrain.lock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.fullAuraBackground

val ROUTINE_CATEGORIES = listOf("sleep", "work", "study", "exercise", "meals", "leisure", "other")

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    autoBlockCategories: Set<String>,
    onToggleCategory: (String, Boolean) -> Unit,
    accessibilityGranted: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    sleepAlarmEnabled: Boolean,
    onToggleSleepAlarm: (Boolean) -> Unit,
    useRoutineSleepWindow: Boolean,
    onToggleUseRoutine: (Boolean) -> Unit,
    wakeMinuteOfDay: Int,
    onWakeMinuteChange: (Int) -> Unit,
    exactAlarmGranted: Boolean,
    alarmSchedulingFailed: Boolean,
    onFixExactAlarm: () -> Unit,
    communityEnabled: Boolean,
    onToggleCommunity: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fullAuraBackground().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Mist300)
            }
            Spacer(Modifier.width(4.dp))
            Text("Settings", style = SecondBrainTypography.headlineMedium, color = Mist100)
        }
        Spacer(Modifier.height(24.dp))

        SbSectionTitle("Schedule auto-block", color = StreakAccent)
        Spacer(Modifier.height(10.dp))
        SbCard {
            Text(
                "Apps with \"Block during schedule windows\" turned on (set per-app on the dashboard) hard-lock automatically during these planner routine categories.",
                style = SecondBrainTypography.bodySmall,
                color = Mist400
            )
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ROUTINE_CATEGORIES) { category ->
                    val selected = category in autoBlockCategories
                    Text(
                        category,
                        style = SecondBrainTypography.bodySmall,
                        color = if (selected) Color.White else Mist300,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) StreakAccent else Ink600)
                            .clickable { onToggleCategory(category, !selected) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SbSectionTitle("Instant blocking", color = StreakAccent)
        Spacer(Modifier.height(10.dp))
        SbCard(topBorderColor = if (accessibilityGranted) StreakAccent else null) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (accessibilityGranted) "Accessibility service is on" else "Accessibility service is off",
                        style = SecondBrainTypography.titleMedium,
                        color = Mist100
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Only reads which app is in front, never screen content. Without it, blocking still works by checking every second or two.",
                        style = SecondBrainTypography.bodySmall,
                        color = Mist300
                    )
                }
            }
            if (!accessibilityGranted) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onOpenAccessibilitySettings,
                    colors = ButtonDefaults.buttonColors(containerColor = StreakAccent.copy(alpha = 0.15f), contentColor = StreakAccent)
                ) { Text("Open Accessibility settings") }
            }
        }

        Spacer(Modifier.height(24.dp))
        SbSectionTitle("Sleep alarm & morning check-in", color = StreakAccent)
        Spacer(Modifier.height(10.dp))
        SbCard(topBorderColor = if (sleepAlarmEnabled) StreakAccent else null) {
            SettingsToggleRow(
                "Wake alarm + wellbeing prompt",
                "A gentle wake-up alarm, then a short move/water/freshen-up prompt before your phone unblocks. Always skippable, always allows emergency calls.",
                sleepAlarmEnabled,
                onToggleSleepAlarm
            )
            if (sleepAlarmEnabled && (!exactAlarmGranted || alarmSchedulingFailed)) {
                Spacer(Modifier.height(18.dp))
                Column {
                    Text(
                        "Alarms can't be scheduled",
                        style = SecondBrainTypography.titleMedium,
                        color = StreakAccent
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Android needs permission to set exact alarms.",
                        style = SecondBrainTypography.bodySmall,
                        color = Mist300
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onFixExactAlarm,
                        colors = ButtonDefaults.buttonColors(containerColor = StreakAccent.copy(alpha = 0.15f), contentColor = StreakAccent)
                    ) { Text("Fix this") }
                }
            }
            if (sleepAlarmEnabled) {
                Spacer(Modifier.height(18.dp))
                SettingsToggleRow(
                    "Use my planner sleep routine",
                    "Wake time follows your \"sleep\" planner routine's end time instead of the time below.",
                    useRoutineSleepWindow,
                    onToggleUseRoutine
                )
                if (!useRoutineSleepWindow) {
                    Spacer(Modifier.height(18.dp))
                    SbSectionTitle("Wake time", color = StreakAccent)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { onWakeMinuteChange(((wakeMinuteOfDay - 15) + 1440) % 1440) }) {
                            Text("−", style = SecondBrainTypography.headlineMedium, color = StreakAccent)
                        }
                        Text(
                            formatMinuteOfDay(wakeMinuteOfDay),
                            style = SecondBrainTypography.displayLarge,
                            color = Mist100,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        IconButton(onClick = { onWakeMinuteChange((wakeMinuteOfDay + 15) % 1440) }) {
                            Text("+", style = SecondBrainTypography.headlineMedium, color = StreakAccent)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SbSectionTitle("Community", color = StreakAccent)
        Spacer(Modifier.height(10.dp))
        SbCard(topBorderColor = if (communityEnabled) StreakAccent else null) {
            SettingsToggleRow(
                "Community rooms",
                "Live chat and video with other users. Can be distracting.",
                communityEnabled,
                onToggleCommunity
            )
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun SettingsToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SecondBrainTypography.titleMedium, color = Mist100)
            Spacer(Modifier.height(4.dp))
            Text(description, style = SecondBrainTypography.bodySmall, color = Mist300)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = StreakAccent, checkedThumbColor = Color.White)
        )
    }
}

private fun formatMinuteOfDay(minuteOfDay: Int): String {
    val hour24 = minuteOfDay / 60
    val minute = minuteOfDay % 60
    val amPm = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return String.format("%d:%02d %s", hour12, minute, amPm)
}
