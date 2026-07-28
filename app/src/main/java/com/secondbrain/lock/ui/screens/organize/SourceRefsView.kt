package com.secondbrain.lock.ui.screens.organize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.network.dto.SourceRef
import com.secondbrain.lock.ui.theme.Mist400

/** Ports InsightCards.js's SourceRefs — one line per ref, formatted per ref.type. */
@Composable
fun SourceRefsView(refs: List<SourceRef>, onOpenNote: (String) -> Unit) {
    if (refs.isEmpty()) return
    Column(Modifier.padding(top = 8.dp)) {
        refs.forEach { ref ->
            val text = when (ref.type) {
                "note" -> "↳ ${ref.title ?: "note"}"
                "mind_insight" -> "↳ ${ref.kind ?: "insight"} insight"
                "resource" -> "↳ ${ref.title ?: ref.url ?: "resource"}" + (if (ref.url != null) " ↗" else "")
                else -> {
                    val suffix = when {
                        ref.total != null -> ": ${ref.followedThrough ?: 0}/${ref.total}"
                        ref.valueDisplay != null -> ": ${ref.valueDisplay}"
                        else -> ""
                    }
                    "↳ ${ref.name ?: ref.type}$suffix"
                }
            }
            val modifier = if (ref.type == "note" && ref.id != null) {
                Modifier.clickable { onOpenNote(ref.id) }
            } else Modifier
            Text(text, color = Mist400, fontSize = 11.sp, modifier = modifier.padding(vertical = 2.dp))
        }
    }
}
