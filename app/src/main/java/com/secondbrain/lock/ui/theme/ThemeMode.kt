package com.secondbrain.lock.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode {
    DARK, LIGHT;

    val storageKey: String get() = if (this == LIGHT) "light" else "dark"

    companion object {
        fun fromStorageKey(key: String?): ThemeMode = if (key == "light") LIGHT else DARK
    }
}

/**
 * Global, observable theme selection. Read from any `@Composable` to get live recomposition
 * on theme change — mirrors the web app's `data-theme` attribute rather than the OS setting.
 */
object SbThemeState {
    var mode by mutableStateOf(ThemeMode.DARK)
}
