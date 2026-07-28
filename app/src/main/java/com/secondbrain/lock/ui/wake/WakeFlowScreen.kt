package com.secondbrain.lock.ui.wake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.ui.theme.Emerald400
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

@Composable
fun WakeFlowScreen(
    phase: WakePhase,
    canSnooze: Boolean,
    canSkipWellbeing: Boolean,
    onStopAlarm: () -> Unit,
    onSnooze: () -> Unit,
    onSetAlarmForLater: () -> Unit,
    onEmergencyCall: () -> Unit,
    onWellbeingDone: () -> Unit,
    onSkipWellbeing: () -> Unit,
    onShowLater: () -> Unit,
    onWriteJournal: () -> Unit,
    onRemindInWebApp: () -> Unit,
    onDismissWithoutJournal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fullAuraBackground()
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (phase) {
            WakePhase.RING -> RingContent(canSnooze, onStopAlarm, onSnooze, onSetAlarmForLater)
            WakePhase.WELLBEING -> WellbeingContent(canSkipWellbeing, onWellbeingDone, onSkipWellbeing, onShowLater)
            WakePhase.JOURNAL -> JournalNudgeContent(onWriteJournal, onRemindInWebApp, onDismissWithoutJournal)
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onEmergencyCall) {
            Text("Emergency call", color = Rose400, style = SecondBrainTypography.bodySmall)
        }
    }
}

@Composable
private fun ColumnScopeCenterLabel(text: String) {
    SbLabel(text)
}

@Composable
private fun ColumnScope.RingContent(
    canSnooze: Boolean,
    onStopAlarm: () -> Unit,
    onSnooze: () -> Unit,
    onSetAlarmForLater: () -> Unit
) {
    Spacer(Modifier.height(48.dp))
    ColumnScopeCenterLabel("Good morning")
    Spacer(Modifier.height(10.dp))
    GradientText("Time to wake up", fontSize = 34.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.height(48.dp))
    Button(
        onClick = onStopAlarm,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = Ink950)
    ) { Text("Stop", fontWeight = FontWeight.Medium, fontSize = 18.sp) }
    if (canSnooze) {
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onSnooze) {
            Text("Snooze 10 min", color = Mist300)
        }
    }
    Spacer(Modifier.height(6.dp))
    TextButton(onClick = onSetAlarmForLater) {
        Text("Set alarm for later", color = Mist400, style = SecondBrainTypography.bodySmall)
    }
}

@Composable
private fun ColumnScope.WellbeingContent(
    canSkip: Boolean,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onShowLater: () -> Unit
) {
    Spacer(Modifier.height(32.dp))
    ColumnScopeCenterLabel("Before you dive in")
    Spacer(Modifier.height(10.dp))
    Text(
        "Freshen up, do your morning yoga, and have breakfast.",
        style = SecondBrainTypography.headlineMedium,
        color = Color.White,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "Other apps are on hold for the next hour so this actually happens.",
        style = SecondBrainTypography.bodyMedium,
        color = Mist400,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(40.dp))
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = Ink950)
    ) { Text("I've done those", fontWeight = FontWeight.Medium) }
    Spacer(Modifier.height(14.dp))
    TextButton(onClick = onShowLater) {
        Text("Show this to me later", color = Mist300)
    }
    if (canSkip) {
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onSkip) {
            Text("Skip for today", color = Mist400, style = SecondBrainTypography.bodySmall)
        }
    }
}

@Composable
private fun ColumnScope.JournalNudgeContent(
    onWriteJournal: () -> Unit,
    onRemindInWebApp: () -> Unit,
    onDismiss: () -> Unit
) {
    Spacer(Modifier.height(48.dp))
    ColumnScopeCenterLabel("One more thing")
    Spacer(Modifier.height(10.dp))
    Text(
        "Want to jot down how you're feeling this morning?",
        style = SecondBrainTypography.headlineMedium,
        color = Color.White,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(36.dp))
    SbCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onWriteJournal,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = Ink950)
            ) { Text("Write here", fontSize = 13.sp) }
            Button(
                onClick = onRemindInWebApp,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Ink600, contentColor = Mist300)
            ) { Text("Remind me in web app", fontSize = 13.sp) }
        }
    }
    Spacer(Modifier.height(14.dp))
    TextButton(onClick = onDismiss) {
        Text("Not now", color = Mist400)
    }
}
