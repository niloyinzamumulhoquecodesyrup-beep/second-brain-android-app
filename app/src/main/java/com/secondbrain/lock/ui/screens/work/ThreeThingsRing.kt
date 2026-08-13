package com.secondbrain.lock.ui.screens.work

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Mist500
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent

private val WORDS = listOf("zero", "one", "two", "three")

/**
 * Three dots, always exactly three (P22: the cap is the point — not a settings knob). Fills on
 * ANY completion (task, routine, focus session), not just tasks. The caption reads "two of
 * three" in words, never "2/3" — digits invite arithmetic, words invite feeling.
 */
@Composable
fun ThreeThingsRing(filled: Int) {
    val clamped = filled.coerceIn(0, 3)
    val allDone = clamped == 3
    var pulsed by remember { mutableStateOf(false) }
    // System "Remove animations" (Settings.Global.ANIMATOR_DURATION_SCALE == 0) collapses this
    // to an instant cross-fade rather than the bouncy scale — vestibular sensitivity is common
    // in this app's audience (P22).
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion = remember {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        }.getOrDefault(false)
    }
    val scale by animateFloatAsState(
        targetValue = if (allDone && !pulsed) 1.25f else 1f,
        animationSpec = if (reduceMotion) {
            androidx.compose.animation.core.tween(0)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        },
        label = "threeThingsPulse",
        finishedListener = { if (allDone) pulsed = true }
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "${WORDS[clamped]} of three things done today" },
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) { index ->
                val dotFilled = index < clamped
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .then(if (allDone) Modifier.scale(scale) else Modifier)
                        .clip(CircleShape)
                        .then(
                            if (dotFilled) {
                                Modifier.background(if (allDone) Emerald400 else StreakAccent)
                            } else {
                                Modifier.border(BorderStroke(2.dp, Ink600), CircleShape)
                            }
                        )
                )
                if (index != 2) Spacer(Modifier.width(12.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            WORDS[clamped].replaceFirstChar(Char::uppercase) + " of three",
            color = Mist500,
            style = SecondBrainTypography.bodySmall
        )
    }
}
