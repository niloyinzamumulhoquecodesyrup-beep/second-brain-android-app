package com.secondbrain.lock.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Bridges the "play my daily briefing" voice command (handled in
 * [VoiceTranscriber][com.secondbrain.lock.util.VoiceTranscriber], which has no view into the
 * compose tree) to [MorningBriefSection][com.secondbrain.lock.ui.screens.work.MorningBriefSection]
 * (which owns the actual playback UI, but only exists while the user is on the Work tab).
 *
 * [isMounted] lets the voice command tell whether that section is even in composition right now —
 * if it's not (user is elsewhere), there's no UI to resurface, so the caller falls back to playing
 * headlessly instead of calling [trigger].
 */
object MorningBriefRequest {
    var id by mutableIntStateOf(0)
        private set

    var isMounted by mutableStateOf(false)

    /** Bumps [id], which [MorningBriefSection][com.secondbrain.lock.ui.screens.work.MorningBriefSection]
     * observes to (re)fetch and play today's cached briefing right now, regardless of the once-a-day
     * ambient gate. Only call this when [isMounted] is true. */
    fun trigger() {
        id++
    }
}
