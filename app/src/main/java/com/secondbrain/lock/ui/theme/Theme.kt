package com.secondbrain.lock.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val SecondBrainDarkScheme = darkColorScheme(
    background = Ink950,
    surface = Ink900,
    surfaceVariant = Ink800,
    primary = Emerald400,
    onPrimary = Ink950,
    secondary = Violet400,
    tertiary = Gold400,
    onBackground = Mist100,
    onSurface = Mist100,
    outline = Ink500,
    error = Rose400
)

@Composable
fun SecondBrainLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SecondBrainDarkScheme,
        typography = SecondBrainTypography,
        content = content
    )
}

/** Recreates the web app's `.bg-aura` radial-glow backdrop. */
fun Modifier.auraBackground(): Modifier = this
    .background(Ink950)
    .drawBehind {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Violet500.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(size.width * 0.12f, 0f),
                radius = size.width * 0.9f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Gold500.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * 0.05f),
                radius = size.width * 0.8f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Emerald500.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height),
                radius = size.width * 1.0f
            )
        )
    }

fun Modifier.fullAuraBackground(): Modifier = this
    .fillMaxSize()
    .auraBackground()
