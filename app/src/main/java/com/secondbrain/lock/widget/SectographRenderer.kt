package com.secondbrain.lock.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.secondbrain.lock.data.RoutineRepository
import com.secondbrain.lock.data.SecurePrefs
import com.secondbrain.lock.data.repo.PlannerRepository
import com.secondbrain.lock.data.repo.TasksRepository
import java.time.LocalDate
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
        // Lighter than the app's near-black page background so the wheel's own "empty" base
        // disc — and anything colored close to it — doesn't visually disappear into the page.
        ring = Color.parseColor("#3A4048"),
        hand = Color.parseColor("#E7E9EB"),
        text = Color.parseColor("#6A717A"),
        categories = mapOf(
            "sleep" to Color.parseColor("#A78BFA"),
            "work" to Color.parseColor("#5EEAD4"),
            "study" to Color.parseColor("#F0D9A3"),
            "exercise" to Color.parseColor("#FB7185"),
            "meals" to Color.parseColor("#A7AEB5"),
            "leisure" to Color.parseColor("#14B8A6"),
            // Was near-black (#2C3238) — indistinguishable from the empty base disc, which
            // made every unset-category item (all one-off scheduled tasks default here) read
            // as an invisible gap rather than a wedge. Needs to actually stand out.
            "other" to Color.parseColor("#8B93A1")
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
            // Was too close to the ring's own base-disc tone (#CFCCDC) for a task wedge to
            // read as distinct from empty time — see the dark-palette note above.
            "other" to Color.parseColor("#6D7280")
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

    private data class Wedge(val startMin: Int, val durationMin: Int, val category: String)

    /**
     * Every wedge the wheel should draw for today — not just recurring routines. Mirrors
     * [com.secondbrain.lock.ui.screens.work.buildTodayItems]'s merge (materialized routine
     * blocks, virtual not-yet-materialized routines, and one-off scheduled tasks) so the
     * compass face never shows less than what Today's own task list shows. Reads the same
     * repository singletons [PlannerRepository]/[TasksRepository] the rest of the app uses —
     * already restored from disk cache at cold start (see CLAUDE.md), so this is safe to call
     * from the widget-update worker without a network round trip.
     */
    private fun buildWedges(): List<Wedge> {
        val today = LocalDate.now()
        val todayStr = today.toString()
        val weekday = RoutineRepository.currentDayOfWeekIndex()

        val blocksToday = PlannerRepository.today.value.blocks.filter {
            it.planDate.take(10) == todayStr && it.routineId != null && it.status != "dismissed"
        }
        val coveredRoutineIds = blocksToday.mapNotNull { it.routineId }.toSet()
        val materialized = blocksToday.map { Wedge(it.startMin, it.durationMin, it.category) }
        val virtual = PlannerRepository.routines.value
            .filter { it.active && weekday in it.days && it.id !in coveredRoutineIds }
            .map { Wedge(it.startMin, it.durationMin, it.category) }

        // A task only gets a wedge once it has a chosen time (set via the quick-add sheet's
        // "Time" picker) — an untimed task has nothing to place on a 24-hour wheel. The
        // "today" test mirrors TasksPanel.isTodayTask exactly: a dated task belongs from its
        // due date onward (due <= today, not due == today, so an overdue task keeps showing up
        // rather than silently vanishing off the wheel), an undated task only on the day it was
        // captured.
        val taskWedges = TasksRepository.tasks.value
            .filter { task ->
                if (task.done || task.startMin == null) return@filter false
                val due = runCatching { task.dueDate?.take(10)?.let(LocalDate::parse) }.getOrNull()
                if (due != null) due <= today
                else runCatching { task.createdAt?.take(10)?.let(LocalDate::parse) }.getOrNull() == today
            }
            .map { Wedge(it.startMin!!, it.durationMin ?: 30, "other") }

        return materialized + virtual + taskWedges
    }

    suspend fun render(context: Context): Bitmap {
        val palette = if (SecurePrefs.getTheme(context) == "light") lightPalette else darkPalette
        val wedges = buildWedges()
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
        wedges.forEach { wedge ->
            wedgePaint.color = palette.categories[wedge.category] ?: palette.categories.getValue("other")
            val startAngle = minuteToAngle(wedge.startMin) - 90f
            val sweep = (wedge.durationMin / 1440f) * 360f
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
        wedges.forEach { wedge ->
            val midMinute = wedge.startMin + wedge.durationMin / 2
            val midAngle = minuteToAngle(midMinute)
            val iconPos = polarPoint(cx, cy, (hubRadius + outerRadius) / 2f, midAngle)
            val fm = iconPaint.fontMetrics
            canvas.drawText(
                CATEGORY_ICONS[wedge.category] ?: CATEGORY_ICONS.getValue("other"),
                iconPos.first,
                iconPos.second - (fm.ascent + fm.descent) / 2f,
                iconPaint
            )

            // Time label curves along the outer edge — rotated to be tangent to the circle at
            // that angle, matching the reference's radial time labels.
            val labelAngle = minuteToAngle(wedge.startMin)
            val labelPos = polarPoint(cx, cy, outerRadius * 0.86f, labelAngle)
            canvas.save()
            // Keep text upright-ish (never upside down) by flipping the rotation on the bottom
            // half of the face.
            val rotation = if (labelAngle in 90f..270f) labelAngle - 90f else labelAngle + 90f
            canvas.rotate(rotation, labelPos.first, labelPos.second)
            canvas.drawText(formatMinuteOfDay(wedge.startMin), labelPos.first, labelPos.second, labelPaint)
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
