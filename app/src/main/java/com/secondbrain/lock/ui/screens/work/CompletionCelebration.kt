package com.secondbrain.lock.ui.screens.work

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.Violet400
import kotlinx.coroutines.delay

/** Mirrors the web's spinning-ring + checkmark completion animation. Auto-dismisses. */
@Composable
fun CompletionCelebration(message: String, bonus: Boolean = false, onDone: () -> Unit) {
    LaunchedEffect(message, bonus) {
        delay(if (bonus) 2000 else 1400)
        onDone()
    }
    val transition = rememberInfiniteTransition(label = "celebrate")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(84.dp)) {
                rotate(angle) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Emerald400, Gold500, Violet400, Emerald400)),
                        startAngle = 0f,
                        sweepAngle = 300f,
                        useCenter = false,
                        style = Stroke(width = 6f)
                    )
                }
            }
            Icon(Icons.Filled.Check, contentDescription = null, tint = Emerald400, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            color = Mist100,
            style = SecondBrainTypography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}
