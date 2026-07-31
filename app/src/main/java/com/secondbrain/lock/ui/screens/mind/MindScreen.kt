package com.secondbrain.lock.ui.screens.mind

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.MindRepository
import com.secondbrain.lock.data.repo.StatsRepository
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.GradientText
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.fullAuraBackground
import kotlinx.coroutines.launch

private enum class MindTab(val label: String) { OVERVIEW("Overview"), LIBRARY("Knowledge Library") }

private suspend fun refreshAll() {
    MindRepository.refreshInsights()
    MindRepository.refreshCycles()
    MindRepository.refreshLibrary()
    MindRepository.refreshTopics()
    MindRepository.refreshSections()
    StatsRepository.refresh()
}

/** Ports pages/mind.js's Overview/Knowledge Library toggle, plus a manual refresh action that
 * triggers POST /api/mind/synthesize (mirrors the web's "Ask Claude Code to refresh it" flow,
 * minus the client-side note-embedding step, which needs an on-device embedding model). */
@Composable
fun MindScreen(
    onOpenNote: (String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    topBar: @Composable () -> Unit = {}
) {
    var tab by remember { mutableStateOf(MindTab.OVERVIEW) }
    var syncing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        launch { refreshAll() }
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
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SbLabel("Mind model", modifier = Modifier.weight(1f))
                if (syncing) {
                    Text("Syncing…", color = Mist400, fontSize = 10.sp, letterSpacing = 1.sp)
                } else {
                    IconButton(
                        onClick = {
                            syncing = true
                            scope.launch {
                                MindRepository.synthesize()
                                refreshAll()
                                syncing = false
                            }
                        },
                        modifier = Modifier.height(24.dp).width(24.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh mind model", tint = Mist300)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row {
                GradientText(tab.label, fontSize = 26.sp, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row {
                MindTab.entries.forEach { candidate ->
                    FilterChip(
                        selected = tab == candidate,
                        onClick = { tab = candidate },
                        label = { Text(candidate.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald400.copy(alpha = 0.25f))
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            when (tab) {
                MindTab.OVERVIEW -> MindOverviewTab(onOpenNote)
                MindTab.LIBRARY -> MindLibraryTab(onOpenNote)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
