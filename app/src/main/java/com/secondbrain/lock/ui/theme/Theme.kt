package com.secondbrain.lock.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun SecondBrainLockTheme(content: @Composable () -> Unit) {
    val scheme = if (SbThemeState.mode == ThemeMode.LIGHT) {
        lightColorScheme(
            background = Ink950,
            surface = Ink900,
            surfaceVariant = Ink800,
            primary = Emerald400,
            onPrimary = Ink900,
            secondary = Violet400,
            tertiary = Gold400,
            onBackground = Mist100,
            onSurface = Mist100,
            outline = Ink500,
            error = Rose400
        )
    } else {
        darkColorScheme(
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
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = SecondBrainTypography,
        content = content
    )
}

/**
 * Recreates the web app's `.bg-aura` radial-glow backdrop, following the active theme. Light
 * mode keeps the original 3-color bloom; dark mode is meant to read as properly dark, so instead
 * of tinting most of the screen with color it gets a single soft spotlight from the top-right
 * corner over the near-black backdrop, purely for depth.
 */
fun Modifier.auraBackground(): Modifier = this
    .background(Ink950)
    .drawBehind {
        if (SbThemeState.mode == ThemeMode.DARK) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Aura1.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.92f, -size.height * 0.05f),
                    radius = size.width * 1.35f
                )
            )
        } else {
            val opacity = AuraOpacity
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Aura1.copy(alpha = 0.14f * opacity), Color.Transparent),
                    center = Offset(size.width * 0.12f, 0f),
                    radius = size.width * 0.9f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Aura2.copy(alpha = 0.12f * opacity), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.05f),
                    radius = size.width * 0.8f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Aura3.copy(alpha = 0.10f * opacity), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height),
                    radius = size.width * 1.0f
                )
            )
        }
    }

fun Modifier.fullAuraBackground(): Modifier = this
    .fillMaxSize()
    .auraBackground()
