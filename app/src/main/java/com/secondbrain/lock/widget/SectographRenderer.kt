package com.secondbrain.lock.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.secondbrain.lock.data.RoutineCache
import com.secondbrain.lock.data.RoutineRepository
import com.secondbrain.lock.data.SecurePrefs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glance widgets can't host arbitrary Canvas drawing (they render to RemoteViews under the
 * hood), so the sectograph face is pre-rendered to a [Bitmap] here and displayed as a plain
 * image — the standard pattern for custom-drawn widget content.
 *
 * Compass style: each routine is a full pie wedge (not a thin ring segment) radiating from a
 * circular hub, with a category icon mid-wedge and its start time as text curved along the
 * outer edge. A red compass needle in the hub points toward the current time.
 */
object SectographRenderer {
    private const val SIZE_PX = 480

    // Same hues as the Compose theme's dark/light accents, hardcoded here since this renders
    // outside any Composable and can't read SbThemeState directly.
    private data class Palette(val background: Int, val ring: Int, val hand: Int, val text: Int, val categories: Map<String, Int>)

    private val darkPalette = Palette(
        background = Color.parseColor("#0A0C0E"),
        ring = Color.parseColor("#22272C"),
        hand = Color.parseColor("#E7E9EB"),
        text = Color.parseColor("#6A717A"),
        categories = mapOf(
            "sleep" to Color.parseColor("#A78BFA"),
            "work" to Color.parseColor("#5EEAD4"),
            "study" to Color.parseColor("#F0D9A3"),
            "exercise" to Color.parseColor("#FB7185"),
            "meals" to Color.parseColor("#A7AEB5"),
            "leisure" to Color.parseColor("#14B8A6"),
            "other" to Color.parseColor("#2C3238")
        )
    )

    private val lightPalette = Palette(
        background = Color.parseColor("#FFFFFF"),
        ring = Color.parseColor("#CFCCDC"),
        hand = Color.parseColor("#30323D"),
        text = Color.parseColor("#757B85"),
        categories = mapOf(
            "sleep" to Color.parseColor("#506B82"),
            "work" to Color.parseColor("#6D57BC"),
            "study" to Color.parseColor("#BD382C"),
            "exercise" to Color.parseColor("#D1264F"),
            "meals" to Color.parseColor("#525662"),
            "leisure" to Color.parseColor("#49378A"),
            "other" to Color.parseColor("#B7B2C8")
        )
    )

    private val NEEDLE_COLOR = Color.parseColor("#FF4B4B")

    private val CATEGORY_ICONS = mapOf(
        "sleep" to "😴", // 😴
        "work" to "💼", // 💼
        "study" to "🎓", // 🎓
        "exercise" to "🏋", // 🏋
        "meals" to "☕", // ☕
        "leisure" to "🎥", // 🎥
        "other" to "📌" // 📌
    )

    suspend fun render(context: Context): Bitmap {
        val palette = if (SecurePrefs.getTheme(context) == "light") lightPalette else darkPalette
        val routines = RoutineRepository(context).getCached().filter {
            it.active && RoutineRepository.currentDayOfWeekIndex() in it.dayList
        }
        val nowMinute = RoutineRepository.currentMinuteOfDay()

        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = SIZE_PX / 2f
        val cy = SIZE_PX / 2f
        val outerRadius = SIZE_PX * 0.47f
        val hubRadius = SIZE_PX * 0.20f

        canvas.drawColor(Color.TRANSPARENT)

        // Base disc first, so any time of day with no routine assigned reads as a filled
        // "empty" wedge instead of a transparent gap showing the page background through —
        // the reference wheel is always a complete, unbroken disc.
        val wedgeRect = RectF(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius)
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = palette.ring }
        canvas.drawOval(wedgeRect, basePaint)

        // Each routine as a full pie wedge (center to outer edge) — the hub circle drawn on top
        // afterward covers each wedge's inner point, turning it into a donut/compass look.
        val wedgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = SIZE_PX * 0.006f; color = palette.background }
        routines.forEach { routine ->
            wedgePaint.color = palette.categories[routine.category] ?: palette.categories.getValue("other")
            val startAngle = minuteToAngle(routine.startMin) - 90f
            val sweep = (routine.durationMin / 1440f) * 360f
            canvas.drawArc(wedgeRect, startAngle, sweep.coerceAtLeast(4f), true, wedgePaint)
            // Thin gap line between wedges so adjacent slices read as distinct, matching the
            // reference's clean wedge separation.
            canvas.drawArc(wedgeRect, startAngle, sweep.coerceAtLeast(4f), true, gapPaint)
        }

        // Icon + start-time label per wedge.
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = SIZE_PX * 0.075f
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = SIZE_PX * 0.042f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        routines.forEach { routine ->
            val midMinute = routine.startMin + routine.durationMin / 2
            val midAngle = minuteToAngle(midMinute)
            val iconPos = polarPoint(cx, cy, (hubRadius + outerRadius) / 2f, midAngle)
            val fm = iconPaint.fontMetrics
            canvas.drawText(
                CATEGORY_ICONS[routine.category] ?: CATEGORY_ICONS.getValue("other"),
                iconPos.first,
                iconPos.second - (fm.ascent + fm.descent) / 2f,
                iconPaint
            )

            // Time label curves along the outer edge — rotated to be tangent to the circle at
            // that angle, matching the reference's radial time labels.
            val labelAngle = minuteToAngle(routine.startMin)
            val labelPos = polarPoint(cx, cy, outerRadius * 0.86f, labelAngle)
            canvas.save()
            // Keep text upright-ish (never upside down) by flipping the rotation on the bottom
            // half of the face.
            val rotation = if (labelAngle in 90f..270f) labelAngle - 90f else labelAngle + 90f
            canvas.rotate(rotation, labelPos.first, labelPos.second)
            canvas.drawText(formatMinuteOfDay(routine.startMin), labelPos.first, labelPos.second, labelPaint)
            canvas.restore()
        }

        // Hub — covers the inner apex of every wedge, giving the donut/compass look.
        val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.background; style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, hubRadius, hubPaint)
        val hubRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.ring; style = Paint.Style.STROKE; strokeWidth = SIZE_PX * 0.008f
        }
        canvas.drawCircle(cx, cy, hubRadius, hubRingPaint)

        // Decorative compass ticks around the inner ring.
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.ring; style = Paint.Style.FILL }
        for (i in 0 until 8) {
            val p = polarPoint(cx, cy, hubRadius * 0.78f, i * 45f)
            canvas.drawCircle(p.first, p.second, SIZE_PX * 0.008f, tickPaint)
        }

        // Red compass needle pointing toward the current time.
        val handAngle = minuteToAngle(nowMinute)
        val needleTip = polarPoint(cx, cy, hubRadius * 0.82f, handAngle)
        val needleBackTip = polarPoint(cx, cy, hubRadius * 0.35f, handAngle + 180f)
        val needleLeft = polarPoint(cx, cy, hubRadius * 0.22f, handAngle - 90f)
        val needleRight = polarPoint(cx, cy, hubRadius * 0.22f, handAngle + 90f)
        val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NEEDLE_COLOR; style = Paint.Style.FILL }
        val needlePath = Path().apply {
            moveTo(needleTip.first, needleTip.second)
            lineTo(needleLeft.first, needleLeft.second)
            lineTo(needleBackTip.first, needleBackTip.second)
            lineTo(needleRight.first, needleRight.second)
            close()
        }
        canvas.drawPath(needlePath, needlePaint)
        canvas.drawCircle(cx, cy, SIZE_PX * 0.02f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.hand })

        return bitmap
    }

    /** 0 minutes (midnight) points straight up, matching a clock face. */
    private fun minuteToAngle(minuteOfDay: Int): Float = (minuteOfDay / 1440f) * 360f

    private fun formatMinuteOfDay(minuteOfDay: Int): String {
        val h = (minuteOfDay / 60) % 24
        val m = minuteOfDay % 60
        return "%02d:%02d".format(h, m)
    }

    private fun polarPoint(cx: Float, cy: Float, radius: Float, angleDegrees: Float): Pair<Float, Float> {
        val radians = Math.toRadians((angleDegrees - 90f).toDouble())
        return Pair(cx + (radius * cos(radians)).toFloat(), cy + (radius * sin(radians)).toFloat())
    }
}
