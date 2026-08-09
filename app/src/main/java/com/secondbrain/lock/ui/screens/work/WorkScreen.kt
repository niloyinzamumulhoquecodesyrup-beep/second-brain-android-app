package com.secondbrain.lock.ui.screens.work

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.data.repo.MindQueueRepository
import com.secondbrain.lock.data.repo.PlannerRepository
import com.secondbrain.lock.data.repo.StatsRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.ui.theme.fullAuraBackground
import kotlinx.coroutines.launch

private data class Celebration(val message: String, val bonus: Boolean)

private val TASK_MESSAGES = listOf("Nice — one down.", "Task cleared.", "Momentum building.", "Keep going.")
private val FOCUS_MESSAGES = listOf("Focus session logged.", "Deep work, done.", "That's real progress.")

// Rare, celebratory lines for the plain variable-ratio bonus (no fresh level to name) — mirrors
// pages/work.js's SURPRISE_LINES exactly.
private val SURPRISE_LINES = listOf(
    "Didn't see that coming, did you? Bonus round.",
    "Extra credit, you weren't even trying for this one.",
    "A little gift from future-you to present-you.",
    "Surprise! The universe noticed.",
    "Unlocked out of nowhere. Enjoy it.",
    "That one was on the house."
)

/** Mirrors pages/work.js: NudgesStrip + RewardPanel + TasksPanel + RoutinePlanner + celebration. */
@Composable
fun WorkScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onOpenStreak: () -> Unit = {},
    onOpenAllTasks: () -> Unit = {},
    topBar: @Composable () -> Unit = {}
) {
    val stats by StatsRepository.stats.collectAsState()
    var celebration by remember { mutableStateOf<Celebration?>(null) }
    // A NudgesStrip task reminder's "Open" sets this; TasksPanel scrolls/glows the matching row
    // then clears it itself once the glow finishes (mirrors pages/work.js's highlightKey).
    var highlightTaskId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        launch { StatsRepository.refresh() }
        launch { TasksRepository.refresh() }
        launch { PlannerRepository.refreshRoutines() }
        launch { PlannerRepository.refreshToday() }
        launch { MindQueueRepository.refresh() }
    }

    // Mirrors pages/work.js's handleCompletion: the stats bump always happens, unconditionally —
    // the celebration variant (level-up vs. plain vs. surprise-bonus) is decided separately, so a
    // 15% bonus roll never skips the real local stats bump the way it used to here.
    fun handleCompletion(type: String) {
        val statsBefore = StatsRepository.stats.value
        val prevTotal = if (type == "task") statsBefore?.tasksDone ?: 0 else statsBefore?.focusSessionsTotal ?: 0
        if (type == "task") StatsRepository.bumpTaskDone() else StatsRepository.bumpFocusSession()
        val nextTotal = prevTotal + 1
        val prevLevel = RewardMath.level(prevTotal)
        val nextLevel = RewardMath.level(nextTotal)
        val dimLabel = if (type == "focus") "Focus" else "Follow-through"
        celebration = when {
            nextLevel > prevLevel -> Celebration("Level up: $dimLabel Lv $nextLevel!", true)
            (0 until 100).random() < 15 -> Celebration(SURPRISE_LINES.random(), true)
            type == "task" -> Celebration(TASK_MESSAGES.random(), false)
            else -> Celebration(FOCUS_MESSAGES.random(), false)
        }
    }

    Box(Modifier.fullAuraBackground().fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp
                )
        ) {
            topBar()
            Spacer(Modifier.height(16.dp))
            MorningBriefSection()
            Spacer(Modifier.height(16.dp))
            NudgesStrip(onOpenTask = { id -> highlightTaskId = id })
            Spacer(Modifier.height(16.dp))
            RewardPanel(stats, onOpenDetail = onOpenStreak)
            Spacer(Modifier.height(16.dp))
            TasksPanel(
                onCompletion = ::handleCompletion,
                highlightTaskId = highlightTaskId,
                onHighlightConsumed = { highlightTaskId = null },
                onSeeAll = onOpenAllTasks
            )
            Spacer(Modifier.height(16.dp))
            RoutinePlanner()
            Spacer(Modifier.height(32.dp))
        }

        celebration?.let { c ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CompletionCelebration(message = c.message, bonus = c.bonus, onDone = { celebration = null })
            }
        }
    }
}
