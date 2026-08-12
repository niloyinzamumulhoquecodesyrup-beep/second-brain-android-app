package com.secondbrain.lock.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.network.dto.TaskBreakdownSubtask
import com.secondbrain.lock.ui.screens.work.BreakdownLoadingDots
import com.secondbrain.lock.ui.screens.work.formatFocusMinutes
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import java.util.UUID

/** A subtask suggested by POST /api/tasks/breakdown, wrapped with a stable [id] — topics/minutes
 * can repeat across a response, so callers add/discard/recurse on a specific row rather than a
 * value that might match more than one. */
internal data class BreakdownRow(
    val subtask: TaskBreakdownSubtask,
    val id: String = UUID.randomUUID().toString()
)

/** Shared presentation for one open breakdown result. Used wherever "break this task down"
 * surfaces (TasksPanel's ⋮ menu / long-press, AllTasksScreen's Today tab, and later the focus
 * screen's own steps checklist) — the suggestion list, chips, quota caption and recursion link
 * are identical everywhere; only what [onAdd] actually DOES differs per caller (schedule a real
 * sibling task vs. append to a checklist). Do not fork this into a second copy — see P13 in the
 * ADHD redesign prompt pack.
 *
 * [subtitleFor] lets a caller describe a row (a computed time window, or nothing) without this
 * composable knowing what a "schedule" is. [addEnabledFor] + [altAction] cover the case where a
 * suggestion can't be added as-is (e.g. it falls after midnight) and needs a different action. */
@Composable
internal fun BreakdownSuggestions(
    rows: List<BreakdownRow>,
    remainingToday: Int?,
    onAdd: (BreakdownRow) -> Unit,
    onDiscard: (BreakdownRow) -> Unit,
    onAddAll: () -> Unit,
    onDiscardAll: () -> Unit,
    onBreakdownFurther: (BreakdownRow) -> Unit,
    modifier: Modifier = Modifier,
    subtitleFor: (BreakdownRow) -> String? = { null },
    addEnabledFor: (BreakdownRow) -> Boolean = { true },
    altAction: (@Composable (BreakdownRow) -> Unit)? = null,
    breakdownFurtherLoadingId: String? = null
) {
    if (rows.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            val isFirst = index == 0
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.subtask.topic, color = Mist100, style = SecondBrainTypography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            subtitleFor(row)?.let {
                                Text(it, color = Mist400, style = SecondBrainTypography.bodySmall)
                                Spacer(Modifier.width(6.dp))
                            }
                            MinutesChip(row.subtask.estimatedMinutes)
                        }
                    }
                    val enabled = addEnabledFor(row)
                    when {
                        // The first suggestion is the one obvious next action — everything after it
                        // is a quiet "+", so the eye lands on one button, not a menu.
                        isFirst && enabled -> Button(
                            onClick = { onAdd(row) },
                            colors = ButtonDefaults.buttonColors(containerColor = StreakAccent),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Start this now", color = Color.White, style = SecondBrainTypography.bodySmall)
                        }
                        enabled -> IconButton(onClick = { onAdd(row) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add subtask", tint = StreakAccent)
                        }
                        else -> altAction?.invoke(row)
                    }
                    Text(
                        "✕",
                        color = Mist400,
                        style = SecondBrainTypography.bodySmall,
                        modifier = Modifier.clickable { onDiscard(row) }.padding(start = 6.dp)
                    )
                }
                // Recursion: an estimate over 20 minutes is exactly where an avoidant user needs
                // one more level of "what's the first physical thing" than a single pass gives.
                if (row.subtask.estimatedMinutes > 20) {
                    Spacer(Modifier.height(4.dp))
                    if (breakdownFurtherLoadingId == row.id) {
                        BreakdownLoadingDots(dotSize = 5.dp, spacing = 4.dp)
                    } else {
                        Text(
                            "Break this down further →",
                            color = StreakAccent,
                            style = SecondBrainTypography.bodySmall,
                            modifier = Modifier.clickable { onBreakdownFurther(row) }
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Add all", color = StreakAccent, style = SecondBrainTypography.bodySmall, modifier = Modifier.clickable(onClick = onAddAll))
            if (remainingToday != null) {
                Text(
                    if (remainingToday == 1) "1 breakdown left today" else "$remainingToday breakdowns left today",
                    color = Mist400,
                    style = SecondBrainTypography.bodySmall
                )
            }
            Text("Discard all", color = Mist400, style = SecondBrainTypography.bodySmall, modifier = Modifier.clickable(onClick = onDiscardAll))
        }
    }
}

@Composable
private fun MinutesChip(minutes: Int) {
    Text(
        formatFocusMinutes(minutes),
        color = Mist300,
        style = SecondBrainTypography.bodySmall,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Ink600.copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
