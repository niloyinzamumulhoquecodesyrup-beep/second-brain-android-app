package com.secondbrain.lock.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.data.SleepPrefs
import com.secondbrain.lock.data.repo.PlannerRepository
import com.secondbrain.lock.ui.screens.work.currentMinuteOfDay
import kotlinx.coroutines.delay

/** 0.0-0.5 Emerald400, 0.5-0.8 Gold400, 0.8-1.0 StreakAccent, lerped continuously — never a hard
 * jump at a boundary, each stop's color is exactly where the neighboring lerp starts/ends. */
private fun timeBarColor(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return when {
        f <= 0.5f -> Emerald400
        f <= 0.8f -> lerp(Emerald400, Gold400, (f - 0.5f) / 0.3f)
        else -> lerp(Gold400, StreakAccent, (f - 0.8f) / 0.2f)
    }
}

private fun resolveWakeMinute(context: android.content.Context, sleepRoutine: com.secondbrain.lock.network.dto.PlannerRoutine?): Int {
    if (!SleepPrefs.getUseRoutineSleepWindow(context) || sleepRoutine == null) return SleepPrefs.getWakeMinuteOfDay(context)
    return (sleepRoutine.startMin + sleepRoutine.durationMin) % 1440
}

private fun resolveSleepMinute(context: android.content.Context, sleepRoutine: com.secondbrain.lock.network.dto.PlannerRoutine?): Int {
    if (!SleepPrefs.getUseRoutineSleepWindow(context) || sleepRoutine == null) return SleepPrefs.getSleepMinuteOfDay(context)
    return sleepRoutine.startMin
}

/**
 * Full-bleed 3dp bar under the top bar on the Today tab — a preattentive "how much of my day is
 * left" signal, absorbed without reading. NO numbers, ever; that's what makes it preattentive
 * rather than another thing to parse. Hidden entirely (zero height) outside the wake-to-sleep
 * window rather than showing a full-red bar at 11pm, which would read as an accusation.
 */
@Composable
fun TimeBar() {
    val context = LocalContext.current
    val routines by PlannerRepository.routines.collectAsState()
    var nowMinute by remember { mutableStateOf(currentMinuteOfDay()) }

    // A 3dp bar doesn't need per-second precision — matches TasksPanel's own "current" dot tick.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMinute = currentMinuteOfDay()
        }
    }

    val sleepRoutine = remember(routines) { routines.firstOrNull { it.category == "sleep" } }
    val wakeMin = remember(sleepRoutine, nowMinute) { resolveWakeMinute(context, sleepRoutine) }
    val sleepMinRaw = remember(sleepRoutine, nowMinute) { resolveSleepMinute(context, sleepRoutine) }
    // A sleep time numerically before the wake time means it falls after midnight (e.g. wake
    // 07:00, sleep 01:00 the following morning) — same overnight-wrap idea TasksPanel's
    // isRoutineCurrentlyRunning already handles, applied here to a single wake/sleep pair instead
    // of a recurring routine.
    val sleepMin = if (sleepMinRaw <= wakeMin) sleepMinRaw + 1440 else sleepMinRaw
    val nowAdjusted = if (nowMinute < wakeMin) nowMinute + 1440 else nowMinute
    val withinDay = nowAdjusted in wakeMin..sleepMin
    val dayLength = (sleepMin - wakeMin).coerceAtLeast(1)
    val fraction = ((nowAdjusted - wakeMin).toFloat() / dayLength).coerceIn(0f, 1f)

    if (!withinDay) return

    val remainingMin = (sleepMin - nowAdjusted).coerceAtLeast(0)
    val remainingHours = remainingMin / 60
    val description = if (remainingHours <= 0) {
        "Less than an hour left in your day"
    } else {
        "About $remainingHours hour${if (remainingHours == 1) "" else "s"} left in your day"
    }

    val trackColor = Ink700
    val tickColor = Ink500
    val fillColor = timeBarColor(fraction)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .semantics { contentDescription = description }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            drawRect(color = trackColor, size = size)
            drawRect(color = fillColor, size = size.copy(width = size.width * fraction))
            listOf(0.25f, 0.5f, 0.75f).forEach { tick ->
                val x = size.width * tick
                drawLine(
                    color = tickColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}
