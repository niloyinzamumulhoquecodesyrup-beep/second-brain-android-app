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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Mirrors the web app's `.card { bg-ink-900 border border-ink-600 rounded-xl }`. Pass [tint] to
 * blend a section's accent color into the card surface — mirrors the Streak card's own tinted
 * (non-plain-Ink900) background, generalized so any card can pick up the same "designed" feel
 * instead of a plain bordered box. */
@Composable
fun SbCard(
    modifier: Modifier = Modifier,
    topBorderColor: Color? = null,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val background = tint?.let { lerp(Ink900, it, 0.14f) } ?: Ink900
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
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

/** A round icon-bubble badge — generalizes the Streak card's icon chips (an emoji centered on a
 * tinted circle) for reuse outside the Work tab, so a card's header can carry a colored icon
 * bullet instead of a plain dot. */
@Composable
fun SbIconChip(icon: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 28.dp, fontSize: TextUnit = 14.sp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) { Text(icon, fontSize = fontSize) }
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

/** The bold eyebrow header used atop a card's hero content — e.g. "YOU'RE DOING GREAT",
 * "TASKS", "ROUTINES". Distinct from [SbLabel]'s lighter-weight, wider-tracked style, which is
 * for secondary in-card sub-labels rather than a card's own title. Uppercases by default; pass
 * `uppercase = false` for copy that's meant to read as a sentence (e.g. NudgesStrip's "When
 * you're ready"), not shouted. */
@Composable
fun SbSectionTitle(text: String, color: Color, modifier: Modifier = Modifier, uppercase: Boolean = true) {
    Text(
        text = if (uppercase) text.uppercase() else text,
        color = color,
        fontSize = 12.sp,
        letterSpacing = 1.4.sp,
        fontWeight = FontWeight.Bold,
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
