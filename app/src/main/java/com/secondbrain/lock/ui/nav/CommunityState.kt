package com.secondbrain.lock.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Global, observable mirror of [com.secondbrain.lock.data.CommunityPrefs] (P11) — same pattern as
 * [com.secondbrain.lock.ui.theme.SbThemeState] for the theme toggle. Read from any `@Composable`
 * for live recomposition when the setting flips (e.g. [Destination.bottomBarOrder]'s getter),
 * rather than requiring an app restart to pick up the change.
 */
object CommunityState {
    var enabled by mutableStateOf(false)
}
