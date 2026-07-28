package com.secondbrain.lock.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.secondbrain.lock.data.RoutineCache
import com.secondbrain.lock.data.RoutineRepository
import com.secondbrain.lock.data.SecurePrefs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glance widgets can't host arbitrary Canvas drawing (they render to RemoteViews under the
 * hood), so the sectograph face is pre-rendered to a [Bitmap] here and displayed as a plain
 * image — the standard pattern for custom-drawn widget content.
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

    suspend fun render(context: Context): Bitmap {
        val palette = if (SecurePrefs.getTheme(context) == "light") lightPalette else darkPalette
        val routines = RoutineRepository(context).getCached()
        val day = RoutineRepository.currentDayOfWeekIndex()
        val nowMinute = RoutineRepository.currentMinuteOfDay()

        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = SIZE_PX / 2f
        val cy = SIZE_PX / 2f
        val faceRadius = SIZE_PX * 0.44f
        val arcStroke = SIZE_PX * 0.09f

        canvas.drawColor(Color.TRANSPARENT)

        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.background; style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, faceRadius, facePaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.ring; style = Paint.Style.STROKE; strokeWidth = SIZE_PX * 0.01f
        }
        canvas.drawCircle(cx, cy, faceRadius, ringPaint)

        // Hour ticks every 3 hours.
        for (hour in 0 until 24 step 3) {
            val angle = minuteToAngle(hour * 60)
            val outer = polarPoint(cx, cy, faceRadius, angle)
            val inner = polarPoint(cx, cy, faceRadius - SIZE_PX * 0.02f, angle)
            canvas.drawLine(inner.first, inner.second, outer.first, outer.second, ringPaint)
        }

        val arcRect = RectF(cx - faceRadius + arcStroke / 2, cy - faceRadius + arcStroke / 2, cx + faceRadius - arcStroke / 2, cy + faceRadius - arcStroke / 2)
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = arcStroke
            strokeCap = Paint.Cap.ROUND
        }
        routines.filter { it.active && day in it.dayList }.forEach { routine ->
            arcPaint.color = palette.categories[routine.category] ?: palette.categories.getValue("other")
            val startAngle = minuteToAngle(routine.startMin) - 90f
            val sweep = (routine.durationMin / 1440f) * 360f
            canvas.drawArc(arcRect, startAngle, sweep.coerceAtLeast(2f), false, arcPaint)
        }

        // Current-time hand.
        val handAngle = minuteToAngle(nowMinute)
        val handEnd = polarPoint(cx, cy, faceRadius - arcStroke * 1.3f, handAngle)
        val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.hand; strokeWidth = SIZE_PX * 0.014f; strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx, cy, handEnd.first, handEnd.second, handPaint)
        canvas.drawCircle(cx, cy, SIZE_PX * 0.02f, handPaint)

        return bitmap
    }

    /** 0 minutes (midnight) points straight up, matching a clock face. */
    private fun minuteToAngle(minuteOfDay: Int): Float = (minuteOfDay / 1440f) * 360f

    private fun polarPoint(cx: Float, cy: Float, radius: Float, angleDegrees: Float): Pair<Float, Float> {
        val radians = Math.toRadians((angleDegrees - 90f).toDouble())
        return Pair(cx + (radius * cos(radians)).toFloat(), cy + (radius * sin(radians)).toFloat())
    }
}
