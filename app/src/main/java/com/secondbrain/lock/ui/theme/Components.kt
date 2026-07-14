package com.secondbrain.lock.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Mirrors the web app's `.card { bg-ink-900 border border-ink-600 rounded-xl }`. */
@Composable
fun SbCard(
    modifier: Modifier = Modifier,
    topBorderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink900)
            .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(16.dp))
    ) {
        if (topBorderColor != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(topBorderColor)
            )
        }
        Column(Modifier.padding(20.dp), content = content)
    }
}

/** Mirrors `.label { text-[11px] uppercase tracking-[0.2em] text-emerald-400 }`. */
@Composable
fun SbLabel(text: String, color: Color = Emerald400, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = color,
        style = SecondBrainTypography.labelSmall,
        modifier = modifier
    )
}

/** Mirrors the `.text-gradient` emerald → gold → violet headline treatment. */
@Composable
fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp,
    textAlign: TextAlign = TextAlign.Start
) {
    val brush = Brush.linearGradient(listOf(Emerald400, Gold500, Violet400))
    Text(
        text = text,
        modifier = modifier,
        style = SecondBrainTypography.headlineLarge.copy(
            fontSize = fontSize,
            textAlign = textAlign,
            brush = brush
        )
    )
}
