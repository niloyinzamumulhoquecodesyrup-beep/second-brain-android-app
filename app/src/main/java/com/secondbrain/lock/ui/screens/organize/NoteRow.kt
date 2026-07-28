package com.secondbrain.lock.ui.screens.organize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.network.dto.Note
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.Violet400

/** Matches lib/paraTheme.js exactly (inbox=rose, project=emerald, area=violet, resource=gold, archive=mist). */
fun paraAccent(para: String) = when (para) {
    "inbox" -> Rose400
    "project" -> Emerald400
    "area" -> Violet400
    "resource" -> Gold500
    else -> Mist400
}

/** Matches components/PARACube.js's iconFor(): a deterministic hash of the note id into a 12-icon set. */
private val PARA_ICONS = listOf("🚀", "🎯", "🛠️", "📐", "🧭", "🔭", "🌱", "🧩", "🎨", "📡", "⚙️", "🏗️")

/**
 * Bit-for-bit port of `hash = (hash*31 + charCode) >>> 0` — JS truncates to *unsigned* 32-bit
 * on every iteration, which is not the same residue as Kotlin's signed Int overflow once the
 * running hash goes negative, so this masks to unsigned 32-bit via Long on every step instead.
 */
fun iconForNote(id: String): String {
    var hash = 0L
    for (c in id) hash = (hash * 31 + c.code) and 0xFFFFFFFFL
    return PARA_ICONS[(hash % PARA_ICONS.size).toInt()]
}

@Composable
fun NoteRow(note: Note, onOpen: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(note.title, color = Mist100, style = SecondBrainTypography.bodyMedium)
            if (note.tags.isNotEmpty()) {
                Text(note.tags.joinToString(" · "), color = Mist400, style = SecondBrainTypography.bodySmall)
            }
        }
        trailing?.invoke()
    }
}
