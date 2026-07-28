package com.secondbrain.lock.ui.screens.organize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.network.dto.Note
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SecondBrainTypography

/** Ports GraduatedSection.js: renders nothing if empty, a plain title-only tile grid (no icon) otherwise. */
@Composable
fun GraduatedSection(notes: List<Note>, onOpenNote: (String) -> Unit) {
    if (notes.isEmpty()) return
    SbCard(topBorderColor = Emerald400) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Emerald400))
            Spacer(Modifier.width(8.dp))
            Text("🎓 Graduated", color = Emerald400, style = SecondBrainTypography.labelSmall)
        }
        Spacer(Modifier.height(14.dp))
        notes.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { note ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(12.dp))
                            .background(Ink950.copy(alpha = 0.4f))
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
