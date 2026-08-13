package com.secondbrain.lock.ui.screens.organize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.network.dto.MindInsight
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.screens.work.StreakSurface
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import kotlin.math.min

private val GOAL_PLATE_COLORS_FALLBACK = listOf(Gold500, Emerald400, Rose400, Mist400, Color(0xFFF0A3C4))

/**
 * Ports InsightCards.js's GoalArrowChart as a genuine Canvas diagram rather than plain rows:
 * one notched ribbon banner per goal, alternating left/right of a central spine, wired by a
 * connector line into a numbered badge on the spine, which terminates in a concentric-ring
 * "target" mark — the same visual idea as the web's ribbon/chevron/target SVG infographic,
 * simplified (no chevron arrowhead shaft, banners are a rounded rect + one triangular notch
 * instead of the web's multi-point polygon, no per-pixel SVG parity). Tapping a banner still
 * expands the full summary + sources below, unchanged from before.
 * Chrome ported to the Streak Section redesign: a tinted [StreakSurface] card, no icon-chip
 * bullet, and the spine/target motif recolored to StreakAccent as the diagram's own structural
 * accent — the per-goal banner palette ([GOAL_PLATE_COLORS_FALLBACK]) is untouched.
 */
@Composable
fun GoalArrowChart(goals: List<MindInsight>, onOpenNote: (String) -> Unit) {
    var activeId by remember { mutableStateOf<String?>(null) }

    StreakSurface {
        SbSectionTitle("Inferred goals", color = Mist300)
        Spacer(Modifier.height(14.dp))
        if (goals.isEmpty()) {
            Text("No goals inferred yet.", color = Mist400, style = SecondBrainTypography.bodySmall)
            return@StreakSurface
        }

        val titles = remember(goals) { goals.map { it.metadataString("name") ?: shortGoalTitle(it.summary) } }
        val rowHeightDp = 56.dp
        val topPadDp = 16.dp
        val targetPadDp = 56.dp
        val canvasHeightDp = topPadDp + rowHeightDp * goals.size + targetPadDp

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeightDp)
                .pointerInput(goals) {
                    detectTapGestures { tap ->
                        val topPadPx = topPadDp.toPx()
                        val rowHeightPx = rowHeightDp.toPx()
                        val idx = ((tap.y - topPadPx) / rowHeightPx).toInt()
                        if (idx in goals.indices) {
                            val id = goals[idx].id
                            activeId = if (activeId == id) null else id
                        }
                    }
                }
        ) {
            val spineX = size.width / 2f
            val topPadPx = topPadDp.toPx()
            val rowHeightPx = rowHeightDp.toPx()
            val targetY = topPadPx + rowHeightPx * goals.size + targetPadDp.toPx() / 2f

            drawLine(
                color = StreakAccent.copy(alpha = 0.35f),
                start = Offset(spineX, topPadPx - 8f),
                end = Offset(spineX, targetY),
                strokeWidth = 3f
            )

            goals.forEachIndexed { i, goal ->
                val color = GOAL_PLATE_COLORS_FALLBACK[i % GOAL_PLATE_COLORS_FALLBACK.size]
                val active = activeId == goal.id
                val cy = topPadPx + rowHeightPx * i + rowHeightPx / 2f
                val side = if (i % 2 == 0) -1f else 1f
                val gap = 26.dp.toPx()
                // Banners hang off the spine at `gap`, so each side only has (spineX - gap) px
                // of room before the canvas edge — sizing purely off the full canvas width (as
                // before) let a banner request more than that and run off-screen.
                val availableHalf = (spineX - gap - 8.dp.toPx()).coerceAtLeast(40.dp.toPx())
                val bannerW = min(size.width * 0.56f, 260.dp.toPx()).coerceAtMost(availableHalf)
                val bannerH = 36.dp.toPx()
                val innerX = spineX + side * gap
                val rectLeft = if (side < 0) innerX - bannerW else innerX
                val rectTop = cy - bannerH / 2f

                drawLine(
                    color = color.copy(alpha = if (active) 0.85f else 0.5f),
                    start = Offset(innerX, cy),
                    end = Offset(spineX, cy),
                    strokeWidth = if (active) 3f else 2f
                )

                drawRoundRect(
                    color = color.copy(alpha = if (active) 0.22f else 0.1f),
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(bannerW, bannerH),
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
                drawRoundRect(
                    color = color.copy(alpha = if (active) 0.9f else 0.4f),
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(bannerW, bannerH),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(width = if (active) 2f else 1.2f)
                )
                val notchTipX = if (side < 0) rectLeft + bannerW + 12.dp.toPx() else rectLeft - 12.dp.toPx()
                val notchBaseX = if (side < 0) rectLeft + bannerW else rectLeft
                val notch = Path().apply {
                    moveTo(notchBaseX, rectTop)
                    lineTo(notchTipX, cy)
                    lineTo(notchBaseX, rectTop + bannerH)
                    close()
                }
                drawPath(notch, color = color.copy(alpha = if (active) 0.95f else 0.7f))

                drawCircle(color = color, radius = 13.dp.toPx(), center = Offset(spineX, cy))

                drawContext.canvas.nativeCanvas.apply {
                    val numberPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 12.sp.toPx()
                        this.color = android.graphics.Color.parseColor("#0B0F14")
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    drawText("${i + 1}", spineX, cy + numberPaint.textSize / 3f, numberPaint)

                    val titlePaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textAlign = if (side < 0) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
                        textSize = 12.sp.toPx()
                        this.color = (if (active) Mist100 else Mist300).toArgb()
                    }
                    val titleX = if (side < 0) rectLeft + 12.dp.toPx() else rectLeft + bannerW - 12.dp.toPx()
                    val label = titles[i].let { t -> if (t.length > 30) t.take(28) + "…" else t }
                    drawText(label, titleX, cy + titlePaint.textSize / 3f, titlePaint)
                }
            }

            drawCircle(StreakAccent.copy(alpha = 0.12f), radius = 22.dp.toPx(), center = Offset(spineX, targetY))
            drawCircle(StreakAccent.copy(alpha = 0.4f), radius = 14.dp.toPx(), center = Offset(spineX, targetY), style = Stroke(width = 2f))
            drawCircle(StreakAccent, radius = 5.dp.toPx(), center = Offset(spineX, targetY))
        }

        val activeIndex = goals.indexOfFirst { it.id == activeId }
        if (activeIndex >= 0) {
            val goal = goals[activeIndex]
            val color = GOAL_PLATE_COLORS_FALLBACK[activeIndex % GOAL_PLATE_COLORS_FALLBACK.size]
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.05f))
                    .border(BorderStroke(1.dp, color.copy(alpha = 0.3f)), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    "Goal %02d%s".format(activeIndex + 1, goal.metadataString("name")?.let { ": $it" } ?: ""),
                    color = color,
                    fontSize = 11.sp,
                    style = SecondBrainTypography.labelSmall
                )
                Spacer(Modifier.height(6.dp))
                Text(goal.summary, color = Mist100, style = SecondBrainTypography.bodySmall)
                SourceRefsView(goal.sourceRefs, onOpenNote)
            }
        }
    }
}

private fun shortGoalTitle(summary: String): String {
    if (summary.isBlank()) return "Inferred goal"
    val firstSentence = summary.split(Regex("(?<=[.!?])\\s")).firstOrNull()?.trim(' ', '.', '!', '?') ?: summary
    if (firstSentence.length <= 32) return firstSentence
    val cut = firstSentence.take(32)
    val lastSpace = cut.lastIndexOf(' ')
    return (if (lastSpace > 16) cut.take(lastSpace) else cut) + "…"
}
