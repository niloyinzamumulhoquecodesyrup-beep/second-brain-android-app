package com.secondbrain.lock.ui.screens.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.network.dto.DayCount
import com.secondbrain.lock.network.dto.Stats
import com.secondbrain.lock.ui.theme.Ink700
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.StreakCard
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Compact, clickable streak card — mirrors the Streak Section redesign's summary screen.
 * Tapping it opens [com.secondbrain.lock.ui.screens.work.StreakDetailScreen], which hosts the
 * badges/gauges/activity-calendar detail this card used to show inline.
 */
@Composable
fun RewardPanel(stats: Stats?, onOpenDetail: () -> Unit) {
    if (stats == null) {
        StreakSurface {
            Text("Loading…", color = Mist400, style = SecondBrainTypography.bodyMedium)
        }
        return
    }
    val computed = remember(stats) { RewardMath.from(stats) }
    StreakSummaryCard(computed = computed, onClick = onOpenDetail)
}

/** The design's `neutralCard`: a 24dp-radius, 20dp-padded card tinted with [StreakCard]. */
@Composable
internal fun StreakSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(StreakCard)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(20.dp),
        content = content
    )
}

/** A round icon bubble matching the design's `iconChip`/`smallIconChip` (bg = Ink900/cardAlt). */
@Composable
internal fun StreakIconChip(icon: String, size: androidx.compose.ui.unit.Dp, fontSize: androidx.compose.ui.unit.TextUnit) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(Ink900),
        contentAlignment = Alignment.Center
    ) { Text(icon, fontSize = fontSize) }
}

/** The design's accent-red circular arrow badge (`cornerArrow`/`cornerArrowSm`). */
@Composable
internal fun StreakArrowBadge(size: androidx.compose.ui.unit.Dp, fontSize: androidx.compose.ui.unit.TextUnit) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(StreakAccent),
        contentAlignment = Alignment.Center
    ) { Text("↗", color = Color.White, fontSize = fontSize) }
}

@Composable
private fun StreakSummaryCard(computed: RewardComputation, onClick: () -> Unit) {
    StreakSurface(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            SbSectionTitle("You're doing great", color = StreakAccent)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tap for stats", color = Mist400, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                StreakArrowBadge(size = 34.dp, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${computed.streak}-day ", color = Mist100, fontSize = 34.sp, fontWeight = FontWeight.Normal)
            Text("streak", color = Mist100, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        }

        computed.next?.let { nextBadge ->
            Spacer(Modifier.height(6.dp))
            Text("Next: ${nextBadge.icon} ${nextBadge.label}", color = Mist300, fontSize = 14.sp)
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)).background(Ink700)) {
            Box(
                Modifier.fillMaxWidth(computed.progressPercent / 100f).height(6.dp).clip(RoundedCornerShape(4.dp)).background(StreakAccent)
            )
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            listOf("📚", "✅", "⏱️", "⚡").forEach { icon ->
                StreakIconChip(icon, size = 44.dp, fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
            }
        }
    }
}

internal fun periodSummary(label: String, totals: PeriodTotals): String =
    "$label: ${totals.notes} notes · ${totals.tasks} tasks · ${totals.focusSessions} focus sessions · " +
        "${formatFocusMinutes(totals.focusMinutes)} focused"

internal fun formatFocusMinutes(total: Int): String {
    if (total <= 0) return "0m"
    val h = total / 60
    val m = total % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

internal data class Gauge(val label: String, val value: Int, val target: Int, val level: Int, val progress: Float)
internal data class Badge(val key: String, val label: String, val icon: String)
internal data class PeriodTotals(val notes: Int, val tasks: Int, val focusSessions: Int, val focusMinutes: Int) {
    val hasData: Boolean get() = notes > 0 || tasks > 0 || focusSessions > 0 || focusMinutes > 0
}

internal data class MilestoneStatus(val badge: Badge, val achieved: Boolean, val progress: Float)

internal data class RewardComputation(
    val streak: Int,
    val headline: String,
    val earned: List<Badge>,
    val next: Badge?,
    val quote: String,
    val gauges: List<Gauge>,
    val week: PeriodTotals,
    val month: PeriodTotals,
    val milestones: List<MilestoneStatus>,
    /** Progress (0-100) toward the next unearned milestone — drives both the summary card's and the detail progress card's bar. */
    val progressPercent: Int
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

    /** Numeric progress toward each badge's threshold, for the milestones list's progress bars. */
    private fun progressToward(def: BadgeDef, s: BadgeStats): Float = when (def.key) {
        "ten_captures" -> s.totalNotes / 10f
        "ten_tasks" -> s.tasksDone / 10f
        "focus_builder" -> s.focusSessionsTotal / 5f
        "deep_focus" -> s.focusSessionsTotal / 25f
        "focus_hour" -> s.focusMinutesTotal / 60f
        "focus_marathon" -> s.focusMinutesTotal / 300f
        "streak_3" -> s.streak / 3f
        "streak_7" -> s.streak / 7f
        else -> if (def.check(s)) 1f else 0f
    }.coerceIn(0f, 1f)

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
        val milestones = BADGE_DEFS.map { def ->
            MilestoneStatus(Badge(def.key, def.label, def.icon), def.check(badgeStats), progressToward(def, badgeStats))
        }

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
            Gauge("Streak", streak, 7, streakLevel, streakProgress),
            Gauge("Captures", todayNotes, medianTarget(stats.capturesByDay), captureLevel, captureProgress),
            Gauge("Tasks done", todayTasks, medianTarget(stats.tasksDoneByDay), taskLevel, taskProgress),
            Gauge("Focus sessions", todayFocus, medianTarget(stats.focusSessionsByDay), focusLevel, focusProgress),
            Gauge("Focus time", todayFocusMinutes, medianTarget(stats.focusMinutesByDay), timeLevel, timeProgress)
        )

        val progressPercent = ((milestones.firstOrNull { !it.achieved }?.progress ?: 1f) * 100).roundToInt()

        return RewardComputation(streak, headline, earned, next, quote, gauges, week, month, milestones, progressPercent)
    }

    /** 5 weeks (35 days) of combined activity level (0-3), oldest first, last entry = today. */
    internal fun activityCalendar(stats: Stats, days: Int = 35): List<Int> {
        val captures = stats.capturesByDay.associate { it.day to it.count }
        val focus = stats.focusSessionsByDay.associate { it.day to it.count }
        val tasks = stats.tasksDoneByDay.associate { it.day to it.count }
        val today = LocalDate.now()
        return (days - 1 downTo 0).map { i ->
            val day = today.minusDays(i.toLong()).toString()
            val total = (captures[day] ?: 0) + (focus[day] ?: 0) + (tasks[day] ?: 0)
            when {
                total <= 0 -> 0
                total <= 2 -> 1
                total <= 4 -> 2
                else -> 3
            }
        }
    }
}
