package com.secondbrain.lock.ui.screens.work

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.network.dto.DayCount
import com.secondbrain.lock.network.dto.Stats
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Ink700
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Orange400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.Sky400
import com.secondbrain.lock.ui.theme.Violet400
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Ports components/RewardPanel.js field-for-field: same streak rule, same 9 badges, same
 * daily quote rotation, same per-dimension level curve and adaptive (7-day median) daily
 * target. The one deliberate simplification is the gauge itself — the web's animated glass
 * "tank" SVG becomes a plain filled capsule here; the numbers, colors, and copy all match.
 */
@Composable
fun RewardPanel(stats: Stats?) {
    SbCard(topBorderColor = Gold500) {
        SbLabel("You're doing great", color = Gold500)
        Spacer(Modifier.height(6.dp))
        if (stats == null) {
            Text("Loading…", color = Mist400, style = SecondBrainTypography.bodyMedium)
            return@SbCard
        }

        val computed = remember(stats) { RewardMath.from(stats) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(
                computed.headline,
                style = SecondBrainTypography.headlineMedium,
                color = Mist100,
                modifier = Modifier.weight(1f)
            )
            if (computed.earned.isNotEmpty()) {
                Row {
                    computed.earned.takeLast(6).forEach { badge ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Gold500.copy(alpha = 0.1f))
                                .border(BorderStroke(1.dp, Gold500.copy(alpha = 0.4f)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text(badge.icon, fontSize = 14.sp) }
                    }
                }
            }
        }

        computed.next?.let { nextBadge ->
            Spacer(Modifier.height(4.dp))
            Text(
                "Next: ${nextBadge.icon} ${nextBadge.label}",
                color = Mist300,
                style = SecondBrainTypography.bodySmall
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            computed.quote,
            color = Mist100,
            style = SecondBrainTypography.bodySmall,
            fontStyle = FontStyle.Italic
        )

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            computed.gauges.forEach { gauge -> GaugeColumn(gauge) }
        }

        // Separate sections, each shown only when that period actually has something to
        // report — an all-zero "This month: 0 notes · 0 tasks..." line reads as broken, not
        // informative, on a fresh account with no history yet.
        if (computed.week.hasData) {
            Spacer(Modifier.height(14.dp))
            Text(periodSummary("This week", computed.week), color = Mist300, style = SecondBrainTypography.bodySmall)
        }
        if (computed.month.hasData) {
            Spacer(Modifier.height(if (computed.week.hasData) 2.dp else 14.dp))
            Text(periodSummary("This month", computed.month), color = Mist300, style = SecondBrainTypography.bodySmall)
        }
    }
}

private fun periodSummary(label: String, totals: PeriodTotals): String =
    "$label: ${totals.notes} notes · ${totals.tasks} tasks · ${totals.focusSessions} focus sessions · " +
        "${formatFocusMinutes(totals.focusMinutes)} focused"

private fun formatFocusMinutes(total: Int): String {
    if (total <= 0) return "0m"
    val h = total / 60
    val m = total % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

internal data class Gauge(val label: String, val value: Int, val target: Int, val color: Color, val level: Int, val progress: Float)
internal data class Badge(val key: String, val label: String, val icon: String)
internal data class PeriodTotals(val notes: Int, val tasks: Int, val focusSessions: Int, val focusMinutes: Int) {
    val hasData: Boolean get() = notes > 0 || tasks > 0 || focusSessions > 0 || focusMinutes > 0
}

internal data class RewardComputation(
    val headline: String,
    val earned: List<Badge>,
    val next: Badge?,
    val quote: String,
    val gauges: List<Gauge>,
    val week: PeriodTotals,
    val month: PeriodTotals
)

/** Exposed for pages/work.js's handleCompletion-equivalent in WorkScreen.kt, which needs just
 * the level number (before/after a stats bump) to decide the level-up celebration. */
object RewardMath {
    private val SEED_THRESHOLDS = listOf(0, 3, 8, 15, 25, 40, 60, 85, 120)
    private val LEVEL_THRESHOLDS: List<Int> by lazy { buildThresholds(24) }

    private fun buildThresholds(count: Int): List<Int> {
        val arr = SEED_THRESHOLDS.toMutableList()
        while (arr.size < count) {
            val last = arr[arr.size - 1]
            val prev = arr[arr.size - 2]
            val step = ((last - prev) * 1.3).roundToInt()
            arr.add(last + maxOf(step, 25))
        }
        return arr
    }

    fun level(total: Int?): Int = levelInfo(total).first

    fun levelInfo(total: Int?): Pair<Int, Float> {
        val t = maxOf(0, total ?: 0)
        var level = 0
        for (i in 1 until LEVEL_THRESHOLDS.size) {
            if (t >= LEVEL_THRESHOLDS[i]) level = i else break
        }
        val current = LEVEL_THRESHOLDS[level]
        val next = LEVEL_THRESHOLDS.getOrNull(level + 1)
        val progress = if (next != null) ((t - current).toFloat() / (next - current)).coerceIn(0f, 1f) else 1f
        return level to progress
    }

    private fun medianTarget(rows: List<DayCount>, days: Int = 7): Int {
        val map = rows.associate { it.day to it.count }
        val today = LocalDate.now()
        val vals = (0 until days).map { i -> map[today.minusDays(i.toLong()).toString()] ?: 0 }.sorted()
        val mid = vals.size / 2
        val median = if (vals.size % 2 == 1) vals[mid].toDouble() else (vals[mid - 1] + vals[mid]) / 2.0
        return maxOf(1, median.roundToInt())
    }

    private fun todayCount(rows: List<DayCount>): Int {
        val today = LocalDate.now().toString()
        return rows.firstOrNull { it.day == today }?.count ?: 0
    }

    private fun sumLastNDays(rows: List<DayCount>, days: Int): Int {
        val map = rows.associate { it.day to it.count }
        val today = LocalDate.now()
        return (0 until days).sumOf { i -> map[today.minusDays(i.toLong()).toString()] ?: 0 }
    }

    private fun computeStreak(stats: Stats): Int {
        val activeDays = (stats.capturesByDay + stats.focusSessionsByDay + stats.tasksDoneByDay)
            .filter { it.count > 0 }
            .mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }
            .toSet()
        var cursor = LocalDate.now()
        if (!activeDays.contains(cursor)) cursor = cursor.minusDays(1)
        var streak = 0
        while (activeDays.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private data class BadgeStats(val totalNotes: Int, val tasksDone: Int, val focusSessionsTotal: Int, val focusMinutesTotal: Int, val streak: Int)
    private data class BadgeDef(val key: String, val label: String, val icon: String, val check: (BadgeStats) -> Boolean)

    private val BADGE_DEFS = listOf(
        BadgeDef("first_capture", "First capture", "🌱") { it.totalNotes >= 1 },
        BadgeDef("ten_captures", "10 notes captured", "📚") { it.totalNotes >= 10 },
        BadgeDef("first_task", "First task done", "✅") { it.tasksDone >= 1 },
        BadgeDef("ten_tasks", "10 tasks done", "🏆") { it.tasksDone >= 10 },
        BadgeDef("first_focus", "First focus session", "⏱️") { it.focusSessionsTotal >= 1 },
        BadgeDef("focus_builder", "5 focus sessions", "🔥") { it.focusSessionsTotal >= 5 },
        BadgeDef("deep_focus", "25 focus sessions", "💎") { it.focusSessionsTotal >= 25 },
        BadgeDef("focus_hour", "1 hour focused", "🕐") { it.focusMinutesTotal >= 60 },
        BadgeDef("focus_marathon", "5 hours focused", "🌙") { it.focusMinutesTotal >= 300 },
        BadgeDef("streak_3", "3-day streak", "⚡") { it.streak >= 3 },
        BadgeDef("streak_7", "7-day streak", "🌟") { it.streak >= 7 }
    )

    private val QUOTES = listOf(
        "Starting is the whole battle. You already won it today.",
        "Momentum doesn't care how small the first step was.",
        "One task, right now. The rest can wait its turn.",
        "You don't need to feel ready. You just need to begin.",
        "A messy start still counts as a start.",
        "Your brain isn't broken, it just runs on different fuel. Feed it a win.",
        "Done beats perfect, every single time.",
        "Five focused minutes is still five minutes you didn't have yesterday.",
        "Progress hides in the boring middle. Keep going.",
        "You showed up. That's the hard part.",
        "Small and consistent outlasts big and occasional.",
        "The next step doesn't have to be the right one, just a real one.",
        "Distraction is loud, but it isn't in charge.",
        "You're not behind. You're exactly where today needed you to start.",
        "Willpower is a muscle, not a personality trait, this rep counts.",
        "Nobody remembers the slow start. They remember the finish.",
        "Rest is not the opposite of progress.",
        "One box checked is proof, not just paperwork.",
        "Today doesn't need to fix every day before it.",
        "Your attention is a resource, spend a little of it here, on purpose."
    )

    internal fun from(stats: Stats): RewardComputation {
        val streak = computeStreak(stats)
        val todayNotes = todayCount(stats.capturesByDay)
        val todayFocus = todayCount(stats.focusSessionsByDay)
        val todayTasks = todayCount(stats.tasksDoneByDay)
        val todayFocusMinutes = todayCount(stats.focusMinutesByDay)
        val week = PeriodTotals(
            notes = sumLastNDays(stats.capturesByDay, 7),
            tasks = sumLastNDays(stats.tasksDoneByDay, 7),
            focusSessions = sumLastNDays(stats.focusSessionsByDay, 7),
            focusMinutes = sumLastNDays(stats.focusMinutesByDay, 7)
        )
        val month = PeriodTotals(
            notes = sumLastNDays(stats.capturesByDay, 30),
            tasks = sumLastNDays(stats.tasksDoneByDay, 30),
            focusSessions = sumLastNDays(stats.focusSessionsByDay, 30),
            focusMinutes = sumLastNDays(stats.focusMinutesByDay, 30)
        )

        val badgeStats = BadgeStats(stats.totalNotes, stats.tasksDone, stats.focusSessionsTotal, stats.focusMinutesTotal, streak)
        val earned = BADGE_DEFS.filter { it.check(badgeStats) }.map { Badge(it.key, it.label, it.icon) }
        val next = BADGE_DEFS.firstOrNull { !it.check(badgeStats) }?.let { Badge(it.key, it.label, it.icon) }

        val headline = when {
            streak > 0 -> "$streak-day streak"
            (todayNotes + todayFocus + todayTasks) > 0 -> "Today's in motion"
            else -> "Ready when you are"
        }

        val quote = QUOTES[LocalDate.now().dayOfYear % QUOTES.size]

        val (streakLevel, streakProgress) = levelInfo(streak)
        val (captureLevel, captureProgress) = levelInfo(stats.totalNotes)
        val (taskLevel, taskProgress) = levelInfo(stats.tasksDone)
        val (focusLevel, focusProgress) = levelInfo(stats.focusSessionsTotal)
        // Raw minutes, same as web's RewardPanel.js — NOT hours; levelInfo's threshold table
        // (0,3,8,15,25,40,60,85,120,...) is shared verbatim across every dimension.
        val (timeLevel, timeProgress) = levelInfo(stats.focusMinutesTotal)

        val gauges = listOf(
            Gauge("Streak", streak, 7, Gold500, streakLevel, streakProgress),
            Gauge("Captures", todayNotes, medianTarget(stats.capturesByDay), Emerald400, captureLevel, captureProgress),
            Gauge("Tasks done", todayTasks, medianTarget(stats.tasksDoneByDay), Violet400, taskLevel, taskProgress),
            Gauge("Focus sessions", todayFocus, medianTarget(stats.focusSessionsByDay), Orange400, focusLevel, focusProgress),
            Gauge("Focus time", todayFocusMinutes, medianTarget(stats.focusMinutesByDay), Sky400, timeLevel, timeProgress)
        )

        return RewardComputation(headline, earned, next, quote, gauges, week, month)
    }
}

@Composable
private fun GaugeColumn(gauge: Gauge) {
    val tankHeight = 56.dp
    val pct = if (gauge.target > 0) (gauge.value.toFloat() / gauge.target).coerceIn(0f, 1f) else 0f

    Column(modifier = Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            gauge.label.uppercase(),
            color = Mist400,
            fontSize = 9.sp,
            style = SecondBrainTypography.labelSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(tankHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(Ink700),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tankHeight * pct)
                    .background(gauge.color)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("${gauge.value}", color = Mist100, style = SecondBrainTypography.bodySmall)
        Spacer(Modifier.height(2.dp))
        Text("Lv ${gauge.level}", color = Mist300, fontSize = 9.sp, style = SecondBrainTypography.labelSmall)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .width(48.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Ink700)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(gauge.progress)
                    .height(3.dp)
                    .background(gauge.color)
            )
        }
    }
}
