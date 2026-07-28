package com.secondbrain.lock.ui.screens.mind

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.MindRepository
import com.secondbrain.lock.network.dto.MindLibraryEntry
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Gold500
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.Violet400

private val ENTRY_TYPES = listOf("concept", "roadmap", "fact", "method")

// Matches pages/mind.js's LIBRARY_ACCENTS — fixed hexes cycled per domain regardless of
// light/dark (the web does the same; these sit on card backgrounds/borders, not text).
private val LIBRARY_ACCENTS = listOf(
    Color(0xFFF0D9A3), Color(0xFF5EEAD4), Color(0xFFB7A6F7),
    Color(0xFFF0A3C4), Color(0xFF96BEFA), Color(0xFFA3E0A0)
)

/** Ports pages/mind.js's Knowledge Library tab: recently-reinforced strip, search, a type
 * filter, click-to-filter domain sidebar, and tap-a-card entry detail — all from the
 * already-fetched /api/mind/library. */
@Composable
fun MindLibraryTab(onOpenNote: (String) -> Unit) {
    val library by MindRepository.library.collectAsState()
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var domainFilter by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var openEntry by remember { mutableStateOf<MindLibraryEntry?>(null) }

    if (library.isEmpty()) {
        SbCard {
            Text(
                "Nothing in the library yet — this fills in as mind cycles run.",
                color = Mist400,
                style = SecondBrainTypography.bodySmall
            )
        }
        return
    }

    val accentByDomain = remember(library) {
        val domains = LinkedHashSet<String>()
        library.forEach { domains.add(it.domain) }
        domains.withIndex().associate { (i, d) -> d to LIBRARY_ACCENTS[i % LIBRARY_ACCENTS.size] }
    }
    val recentlyReinforced = remember(library) {
        library.sortedByDescending { it.lastReinforcedAt ?: it.firstLearnedAt.orEmpty() }.take(6)
    }
    val domains = remember(library) {
        library.groupingBy { it.domain }.eachCount().toList().sortedByDescending { it.second }
    }
    val filtered = remember(library, typeFilter, domainFilter, search) {
        val q = search.trim().lowercase()
        library.filter { e ->
            (domainFilter == null || e.domain == domainFilter) &&
                (typeFilter == null || e.entryType == typeFilter) &&
                (q.isEmpty() || "${e.title} ${e.summary} ${e.domain}".lowercase().contains(q))
        }
    }

    SbCard(topBorderColor = Emerald400) {
        SbLabel("Recently reinforced", color = Emerald400)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            recentlyReinforced.forEach { entry ->
                LibraryCard(
                    entry,
                    accent = accentByDomain[entry.domain] ?: Violet400,
                    modifier = Modifier.width(200.dp).padding(end = 10.dp),
                    onClick = { openEntry = entry }
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    LibrarySearchField(search, onValueChange = { search = it })

    Spacer(Modifier.height(10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        FilterChip(
            selected = typeFilter == null,
            onClick = { typeFilter = null },
            label = { Text("All types") },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Violet400.copy(alpha = 0.25f)),
            modifier = Modifier.padding(end = 6.dp)
        )
        ENTRY_TYPES.forEach { type ->
            FilterChip(
                selected = typeFilter == type,
                onClick = { typeFilter = type },
                label = { Text(type.replaceFirstChar { it.uppercase() }) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Violet400.copy(alpha = 0.25f)),
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
    if (domainFilter != null) {
        Spacer(Modifier.height(6.dp))
        FilterChip(
            selected = true,
            onClick = { domainFilter = null },
            label = { Text("$domainFilter ✕") },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Gold500.copy(alpha = 0.25f))
        )
    }

    Spacer(Modifier.height(12.dp))
    SbLabel("${domainFilter ?: "Library"} (${filtered.size})", color = Violet400)
    Spacer(Modifier.height(8.dp))
    if (filtered.isEmpty()) {
        Text("No entries match.", color = Mist400, style = SecondBrainTypography.bodySmall)
    } else {
        Column {
            filtered.forEach { entry ->
                LibraryCard(
                    entry,
                    accent = accentByDomain[entry.domain] ?: Violet400,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openEntry = entry }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    Spacer(Modifier.height(6.dp))
    SbCard(topBorderColor = Gold500) {
        SbLabel("Domains", color = Gold500)
        Spacer(Modifier.height(8.dp))
        domains.forEach { (domain, count) ->
            val active = domainFilter == domain
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { domainFilter = if (active) null else domain }
                    .background(if (active) Ink600.copy(alpha = 0.4f) else Color.Transparent)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(50)).background(accentByDomain[domain] ?: Violet400))
                Spacer(Modifier.width(8.dp))
                Text(domain, color = if (active) Mist100 else Mist300, style = SecondBrainTypography.bodySmall, modifier = Modifier.weight(1f))
                Text("$count", color = Mist400, style = SecondBrainTypography.bodySmall)
            }
        }
    }

    openEntry?.let { entry ->
        MindLibraryDetailDialog(
            entry = entry,
            accent = accentByDomain[entry.domain] ?: Violet400,
            onOpenNote = onOpenNote,
            onDismiss = { openEntry = null }
        )
    }
}

@Composable
private fun LibrarySearchField(value: String, onValueChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Ink900)
            .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) {
            Text("Search the library…", color = Mist400, style = SecondBrainTypography.bodySmall)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = Mist100, fontSize = 13.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Emerald400),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LibraryCard(entry: MindLibraryEntry, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SbCard(modifier = modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(entry.entryType.uppercase(), color = accent, fontSize = 9.sp, style = SecondBrainTypography.labelSmall)
            if (!entry.surfaced) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .border(BorderStroke(1.dp, Ink600), RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("background research", color = Mist400, fontSize = 9.sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(entry.title, color = Mist100, style = SecondBrainTypography.titleMedium)
        if (entry.summary.isNotBlank() && entry.summary != entry.title) {
            Spacer(Modifier.height(4.dp))
            Text(entry.summary, color = Mist300, style = SecondBrainTypography.bodySmall, maxLines = 3)
        }
        Spacer(Modifier.height(6.dp))
        // No explicit mastery rating field ships from the server — approximated from how many
        // mind cycles have reinforced this entry, capped at 5 (web's exact star logic isn't
        // exposed via the API to match precisely).
        Text("★".repeat(entry.cycleCount.coerceIn(1, 5)) + "☆".repeat(5 - entry.cycleCount.coerceIn(1, 5)), color = Gold500, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(entry.domain, color = Mist400, style = SecondBrainTypography.bodySmall)
    }
}
