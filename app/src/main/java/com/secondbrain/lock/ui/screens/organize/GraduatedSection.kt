package com.secondbrain.lock.ui.screens.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.network.dto.Note
import com.secondbrain.lock.ui.screens.work.StreakSurface
import com.secondbrain.lock.ui.theme.Ink800
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent

/** Ported to the Streak Section redesign's visual language (see RewardPanel/RoutinePlanner in
 * ui/screens/work/ and DashboardScreen.kt's own migration): a tinted [StreakSurface] card with a
 * StreakAccent eyebrow title, and uniformly Ink800-tinted tiles replacing the old bordered
 * Ink950/Emerald SbCard grid. */
@Composable
fun GraduatedSection(notes: List<Note>, onOpenNote: (String) -> Unit) {
    if (notes.isEmpty()) return
    StreakSurface {
        SbSectionTitle("Graduated", color = StreakAccent)
        Spacer(Modifier.height(14.dp))
        notes.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { note ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Ink800)
                            .clickable { onOpenNote(note.id) }
                            .padding(14.dp)
                    ) {
                        Text(
                            note.title,
                            color = Mist100,
                            style = SecondBrainTypography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
