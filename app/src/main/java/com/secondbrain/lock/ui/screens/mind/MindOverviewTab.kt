package com.secondbrain.lock.ui.screens.mind

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.MindRepository
import com.secondbrain.lock.data.repo.StatsRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.dto.DayCount
import com.secondbrain.lock.network.dto.MindCycleRun
import com.secondbrain.lock.network.dto.MindInsight
import com.secondbrain.lock.network.dto.ParaCounts
import com.secondbrain.lock.network.dto.SourceRef
import com.secondbrain.lock.network.dto.Stats
import com.secondbrain.lock.ui.screens.organize.paraAccent
import com.secondbrain.lock.ui.screens.work.StreakSurface
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun MindOverviewTab(onOpenNote: (String) -> Unit) {
    val cycles by MindRepository.cycles.collectAsState()
    val insights by MindRepository.insights.collectAsState()
    val stats by StatsRepository.stats.collectAsState()
    val sections by MindRepository.sections.collectAsState()
    val topics by MindRepository.topics.collectAsState()
    val library by MindRepository.library.collectAsState()

    val feedItems = remember(sections) { feedItemsFrom(sections) }
    if (feedItems.isNotEmpty()) {
        MindNewsTicker(feedItems)
        Spacer(Modifier.height(16.dp))
    }

    CycleHealthCard(cycles?.latest)

    Spacer(Modifier.height(16.dp))
    WholePictureCard(stats, insights?.overview)

    Spacer(Modifier.height(16.dp))
    RemindersCard(insights?.byKind?.openLoop.orEmpty(), onOpenNote)

    Spacer(Modifier.height(16.dp))
    StreakSurface {
        SbSectionTitle("Attention patterns", color = StreakAccent)
        Spacer(Modifier.height(10.dp))
        AttentionChart(stats?.capturesByDay.orEmpty(), insights?.byKind?.attentionPattern?.firstOrNull()?.summary)
    }

    Spacer(Modifier.height(16.dp))
    InterestClusterCityMap(
        topics = topics,
        goals = insights?.byKind?.inferredGoal.orEmpty(),
        library = library,
        onOpenNote = onOpenNote
    )
}

@Composable
private fun CycleHealthCard(latest: MindCycleRun?) {
    var showNotes by remember(latest?.id) { mutableStateOf(false) }
    val (dotColor, label) = when (latest?.status) {
        "partial" -> Gold500 to "partial"
        "error" -> Rose400 to "error"
        else -> Emerald400 to "ok"
    }
    StreakSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(6.dp))
            SbLabel(if (latest == null) "Mind cycle" else "Last cycle $label", color = dotColor)
        }
        Spacer(Modifier.height(6.dp))
        if (latest == null) {
            Text("No cycles yet — this fills in after the first mind cycle runs.", color = Mist400, style = SecondBrainTypography.bodySmall)
        } else {
            Text(daysAgoText(latest.completedAt ?: latest.startedAt), color = Mist300, style = SecondBrainTypography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CycleStat(latest.insightsWritten ?: 0, "Insights")
                CycleStat(latest.sectionsWritten ?: 0, "Sections")
                CycleStat(latest.tokensUsed ?: 0, "~Tokens")
            }
            if (!latest.notes.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { showNotes = !showNotes }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (showNotes) "Hide details" else "Show details", color = Mist400, fontSize = 11.sp)
                }
                if (showNotes) {
                    Text(latest.notes, color = Mist400, style = SecondBrainTypography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CycleStat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", color = Mist100, style = SecondBrainTypography.titleLarge)
        Spacer(Modifier.height(2.dp))
        Text(label.uppercase(), color = Mist400, fontSize = 9.sp, style = SecondBrainTypography.labelSmall)
    }
}

private val PARA_DONUT_ORDER = listOf("project", "area", "resource", "archive", "inbox")
private val PARA_LABELS = mapOf(
    "inbox" to "Inbox", "project" to "Projects", "area" to "Areas", "resource" to "Resources", "archive" to "Archive"
)

@Composable
private fun WholePictureCard(stats: Stats?, overview: MindInsight?) {
    var showText by remember { mutableStateOf(false) }
    StreakSurface {
        SbSectionTitle("The whole picture", color = StreakAccent)
        Spacer(Modifier.height(10.dp))
        if (stats == null) {
            Text("Loading…", color = Mist400, style = SecondBrainTypography.bodySmall)
        } else {
            ParaDonut(stats.para)
        }
        if (!overview?.summary.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { showText = !showText }, contentPadding = PaddingValues(0.dp)) {
                Text(if (showText) "Hide narrative" else "Read the narrative", color = Mist400, fontSize = 11.sp)
            }
            if (showText) {
                Text(overview!!.summary, color = Mist300, style = SecondBrainTypography.bodySmall)
            }
        }
    }
}

@Composable
private fun ParaDonut(para: ParaCounts) {
    val buckets = remember(para) {
        PARA_DONUT_ORDER.map { it to paraCount(para, it) }.filter { it.second > 0 }
    }
    val total = buckets.sumOf { it.second }
    if (total == 0) {
        Text("No notes yet.", color = Mist400, style = SecondBrainTypography.bodySmall)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val strokeWidth = 20.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                var startAngle = -90f
                buckets.forEach { (key, count) ->
                    val sweep = 360f * count / total
                    drawArc(
                        color = paraAccent(key),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", color = Mist100, style = SecondBrainTypography.titleLarge)
                Text("NOTES", color = Mist400, fontSize = 9.sp, style = SecondBrainTypography.labelSmall)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            buckets.forEach { (key, count) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(paraAccent(key)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${PARA_LABELS[key]}: $count note${if (count == 1) "" else "s"}",
                        color = Mist300,
                        style = SecondBrainTypography.bodySmall
                    )
                }
            }
        }
    }
}

private fun paraCount(para: ParaCounts, key: String): Int = when (key) {
    "inbox" -> para.inbox
    "project" -> para.project
    "area" -> para.area
    "resource" -> para.resource
    else -> para.archive
}

/** Ports pages/mind.js's parseReminder — day-count + title parsed out of the open_loop template. */
private data class ParsedReminder(val id: String, val title: String, val days: Int?, val noteId: String?)

private val DAYS_REGEX = Regex("""(\d+)\s*days?""")
private val QUOTED_REGEX = Regex(""""([^"]+)"""")

private fun parseReminder(insight: MindInsight): ParsedReminder {
    val summary = insight.summary
    val days = DAYS_REGEX.find(summary)?.groupValues?.get(1)?.toIntOrNull()
    val noteRef: SourceRef? = insight.sourceRefs.firstOrNull { it.type == "note" }
    val title = noteRef?.title ?: QUOTED_REGEX.find(summary)?.groupValues?.get(1) ?: summary.take(40)
    return ParsedReminder(insight.id, title, days, noteRef?.id)
}

/**
 * Was reading RemindersRepository (due-date reminders from GET /api/reminders — a different
 * feature, still correctly used by the Work tab's NudgesStrip). Fixed to read mind_insights'
 * open_loop kind, matching pages/mind.js:298-368: notes that sat around with no task/packet.
 */
@Composable
private fun RemindersCard(openLoop: List<MindInsight>, onOpenNote: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var added by remember { mutableStateOf(setOf<String>()) }

    StreakSurface {
        SbSectionTitle("Reminders", color = StreakAccent)
        Spacer(Modifier.height(10.dp))
        if (openLoop.isEmpty()) {
            Text("No reminders right now.", color = Mist400, style = SecondBrainTypography.bodySmall)
        } else {
            val rows = remember(openLoop) { openLoop.map(::parseReminder) }
            rows.forEach { r ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.title,
                            color = Mist100,
                            style = SecondBrainTypography.bodyMedium,
                            modifier = if (r.noteId != null) Modifier.clickable { onOpenNote(r.noteId) } else Modifier
                        )
                        if (r.days != null) {
                            Text("sitting for ${r.days} day${if (r.days == 1) "" else "s"}", color = Mist400, style = SecondBrainTypography.bodySmall)
                        }
                    }
                    val isAdded = r.id in added
                    TextButton(
                        onClick = {
                            added = added + r.id
                            scope.launch {
                                TasksRepository.create(title = r.title, dueDate = LocalDate.now().toString(), noteId = r.noteId)
                            }
                        },
                        enabled = !isAdded
                    ) {
                        Text(if (isAdded) "✓ added" else "+ add to my day", color = StreakAccent, style = SecondBrainTypography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionChart(rows: List<DayCount>, caption: String?) {
    val sorted = rows.sortedBy { it.day }
    if (sorted.size < 2) {
        Text(
            caption ?: "Not enough activity yet to chart attention over time.",
            color = Mist400,
            style = SecondBrainTypography.bodySmall
        )
        return
    }
    val maxCount = (sorted.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)

    Text("peak $maxCount", color = Gold500, style = SecondBrainTypography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
        val stepX = if (sorted.size > 1) size.width / (sorted.size - 1) else 0f
        val points = sorted.mapIndexed { i, dc ->
            Offset(i * stepX, size.height - (dc.count.toFloat() / maxCount) * size.height)
        }
        for (i in 0 until points.size - 1) {
            drawLine(color = Emerald400, start = points[i], end = points[i + 1], strokeWidth = 4f)
        }
        points.forEach { p -> drawCircle(color = Emerald400, radius = 5f, center = p) }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val markers = listOfNotNull(sorted.firstOrNull(), sorted.getOrNull(sorted.size / 2), sorted.lastOrNull())
        markers.forEach { dc -> Text(formatShortDate(dc.day), color = Mist400, fontSize = 9.sp) }
    }
    if (!caption.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(caption, color = Mist400, style = SecondBrainTypography.bodySmall)
    }
}

private fun formatShortDate(day: String): String =
    runCatching { LocalDate.parse(day) }.getOrNull()?.let { "${it.monthValue}/${it.dayOfMonth}" } ?: day

private fun daysSince(createdAt: String?): Long {
    val instant = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return 0
    return ChronoUnit.DAYS.between(instant, Instant.now()).coerceAtLeast(0)
}

private fun daysAgoText(timestamp: String?): String {
    val days = daysSince(timestamp)
    return when (days) {
        0L -> "Completed today"
        1L -> "1 day ago"
        else -> "$days days ago"
    }
}
