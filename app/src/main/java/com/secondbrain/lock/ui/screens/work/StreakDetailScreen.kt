package com.secondbrain.lock.ui.screens.work

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.StatsRepository
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink700
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.StreakCallisto
import com.secondbrain.lock.ui.theme.StreakCard
import com.secondbrain.lock.ui.theme.StreakSilver
import com.secondbrain.lock.ui.theme.fullAuraBackground

/** The page the compact streak card opens into — mirrors the Streak Section redesign's "detail" screen. */
@Composable
fun StreakDetailScreen(onBack: () -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    val stats by StatsRepository.stats.collectAsState()

    Box(Modifier.fullAuraBackground().fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp
                )
        ) {
            StreakDetailHeader(onBack)

            val currentStats = stats
            if (currentStats == null) {
                Spacer(Modifier.height(24.dp))
                Text("Loading…", color = Mist400, style = SecondBrainTypography.bodyMedium)
                return@Column
            }

            val computed = remember(currentStats) { RewardMath.from(currentStats) }
            val calendar = remember(currentStats) { RewardMath.activityCalendar(currentStats) }

            Spacer(Modifier.height(18.dp))
            HeroCard(computed)

            Spacer(Modifier.height(22.dp))
            ProgressCard(computed)

            Spacer(Modifier.height(26.dp))
            SectionLabel("CATEGORIES")
            Spacer(Modifier.height(8.dp))
            CategoryTiles(computed)

            Spacer(Modifier.height(26.dp))
            SectionLabel("ACTIVITY · LAST 5 WEEKS")
            Spacer(Modifier.height(10.dp))
            ActivityCalendar(calendar)

            Spacer(Modifier.height(26.dp))
            SectionLabel("SUMMARY")
            Spacer(Modifier.height(10.dp))
            SummaryTiles(computed)

            Spacer(Modifier.height(26.dp))
            SectionLabel("MILESTONES")
            Spacer(Modifier.height(10.dp))
            MilestonesList(computed)
        }
    }
}

@Composable
private fun StreakDetailHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Ink900)
                .border(1.dp, Ink600, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("←", color = Mist100, fontSize = 16.sp) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("STATISTICS", color = Mist400, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Row {
                Text("Your streak, ", color = Mist100, fontSize = 26.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp)
                Text("in numbers", color = Mist100, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Mist400, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
}

@Composable
private fun HeroCard(computed: RewardComputation) {
    val streakGauge = computed.gauges.first { it.label == "Streak" }
    StreakSurface {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            StreakRing(progress = streakGauge.progress, size = 132.dp, stroke = 10.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥", fontSize = 30.sp)
                    Text("${computed.streak}", color = Mist100, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Column {
                Text("Day streak · Level ${streakGauge.level}", color = Mist300, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    computed.quote,
                    color = Mist400,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.widthIn(max = 170.dp)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("📚", "✅", "⏱️", "⚡").forEach { icon -> StreakIconChip(icon, size = 44.dp, fontSize = 16.sp) }
        }
    }
}

/** A ring gauge matching the design's masked conic-gradient — flat (butt-cap) progress arc over a full track ring. */
@Composable
private fun StreakRing(progress: Float, size: Dp, stroke: Dp, content: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            drawArc(
                color = Ink700,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.toPx() - strokePx, size.toPx() - strokePx),
                style = Stroke(strokePx)
            )
            drawArc(
                color = StreakAccent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.toPx() - strokePx, size.toPx() - strokePx),
                style = Stroke(strokePx)
            )
        }
        content()
    }
}

@Composable
private fun ProgressCard(computed: RewardComputation) {
    val nextMilestone = computed.milestones.firstOrNull { !it.achieved }
    val percent = computed.progressPercent
    StreakSurface {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Text("YOUR PROGRESS", color = Mist400, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$percent", color = Mist100, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp)
                    Text("%", color = StreakAccent, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Of the next milestone" + (nextMilestone?.let { " · ${it.badge.icon} ${it.badge.label}" } ?: ""),
                    color = Mist300,
                    fontSize = 13.sp
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(StreakAccent))
                Box(Modifier.size(15.dp).clip(CircleShape).background(StreakCallisto))
                Box(Modifier.size(12.dp).clip(CircleShape).background(StreakSilver))
                Box(Modifier.size(9.dp).clip(CircleShape).background(Ink600))
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(5.dp)).background(Ink700)) {
            Box(Modifier.fillMaxWidth(percent / 100f).height(8.dp).clip(RoundedCornerShape(5.dp)).background(StreakAccent))
        }
    }
}

@Composable
private fun CategoryTile(
    icon: String,
    label: String,
    value: String,
    level: Int,
    progress: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    StreakSurface(modifier = modifier) {
        StreakIconChip(icon, size = 34.dp, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Text(label, color = Mist400, fontSize = 12.sp, letterSpacing = 0.4.sp)
        Text(value, color = Mist100, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(2.dp))
        Text("Level $level", color = Mist300, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)).background(Ink700)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
        }
    }
}

@Composable
private fun FocusTimeTile(value: String, level: Int, progress: Float) {
    StreakSurface {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StreakIconChip("⚡", size = 34.dp, fontSize = 16.sp)
            Column {
                Text("FOCUS TIME", color = Mist400, fontSize = 12.sp, letterSpacing = 0.4.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, color = Mist100, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text(" · Level $level", color = Mist300, fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)).background(Ink700)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(4.dp)).background(StreakSilver))
        }
    }
}

@Composable
private fun CategoryTiles(computed: RewardComputation) {
    val gauges = computed.gauges.associateBy { it.label }
    val streak = gauges.getValue("Streak")
    val captures = gauges.getValue("Captures")
    val tasks = gauges.getValue("Tasks done")
    val sessions = gauges.getValue("Focus sessions")
    val time = gauges.getValue("Focus time")

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryTile("🔥", "STREAK", "${streak.value}", streak.level, streak.progress, StreakAccent, Modifier.weight(1f))
            CategoryTile("📚", "CAPTURES", "${captures.value}", captures.level, captures.progress, StreakSilver, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryTile("✅", "TASKS DONE", "${tasks.value}", tasks.level, tasks.progress, StreakSilver, Modifier.weight(1f))
            CategoryTile("⏱️", "SESSIONS", "${sessions.value}", sessions.level, sessions.progress, StreakSilver, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        FocusTimeTile(formatFocusMinutes(time.value), time.level, time.progress)
    }
}

/** A plain (non-lazy) 7-column grid, sized to its own content — [calendar] is only ever 35 items,
 * so there's no need for LazyVerticalGrid's virtualization, and a fixed-height Lazy grid nested
 * inside this screen's own scrollable Column was clipping rows instead of showing all 5 weeks. */
@Composable
private fun ActivityCalendar(calendar: List<Int>) {
    StreakSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            calendar.chunked(7).forEachIndexed { rowIndex, week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    week.forEachIndexed { colIndex, level ->
                        val index = rowIndex * 7 + colIndex
                        val isToday = index == calendar.lastIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .then(if (isToday) Modifier.border(2.dp, Mist100, RoundedCornerShape(7.dp)) else Modifier)
                        ) { ActivityCell(level) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCell(level: Int) {
    when (level) {
        0 -> DiagonalStripes()
        1 -> Box(Modifier.fillMaxSize().background(Ink700))
        2 -> Box(Modifier.fillMaxSize().background(StreakSilver.copy(alpha = 0.55f)))
        else -> Box(Modifier.fillMaxSize().background(StreakAccent))
    }
}

/** Approximates the design's `repeating-linear-gradient(45deg, border 0 2px, transparent 2px 6px)` empty-day texture. */
@Composable
private fun DiagonalStripes() {
    Canvas(Modifier.fillMaxSize()) {
        val stripe = 2.dp.toPx()
        val period = 6.dp.toPx()
        val diagonal = size.width + size.height
        rotate(45f) {
            var x = -diagonal
            while (x < diagonal) {
                drawRect(
                    color = Ink600,
                    topLeft = Offset(x, -diagonal / 2f),
                    size = androidx.compose.ui.geometry.Size(stripe, diagonal * 2f)
                )
                x += period
            }
        }
    }
}

@Composable
private fun SummaryTiles(computed: RewardComputation) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryTile("THIS WEEK", computed.week, Modifier.weight(1f))
        SummaryTile("THIS MONTH", computed.month, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryTile(label: String, totals: PeriodTotals, modifier: Modifier) {
    StreakSurface(modifier = modifier) {
        Text(label, color = Mist400, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
        Spacer(Modifier.height(10.dp))
        SummaryRow("Notes", "${totals.notes}")
        SummaryRow("Tasks", "${totals.tasks}")
        SummaryRow("Sessions", "${totals.focusSessions}")
        SummaryRow("Focused", formatFocusMinutes(totals.focusMinutes))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Mist300, fontSize = 13.sp)
        Text(value, color = Mist100, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MilestonesList(computed: RewardComputation) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        computed.milestones.forEach { milestone ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(StreakCard)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    milestone.badge.icon,
                    fontSize = 20.sp,
                    modifier = if (!milestone.achieved && milestone.progress <= 0f) Modifier.alpha(0.35f) else Modifier
                )
                Column(Modifier.weight(1f)) {
                    Text(milestone.badge.label, color = if (milestone.achieved) Mist100 else Mist300, fontSize = 14.sp)
                    when {
                        milestone.achieved -> Text("Achieved", color = Mist300, fontSize = 12.sp)
                        milestone.progress > 0f -> {
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth(0.5f).height(5.dp).clip(RoundedCornerShape(4.dp)).background(Ink700)) {
                                Box(
                                    Modifier.fillMaxWidth(milestone.progress).height(5.dp).clip(RoundedCornerShape(4.dp)).background(StreakAccent)
                                )
                            }
                        }
                        else -> Text("Locked", color = Mist400, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
