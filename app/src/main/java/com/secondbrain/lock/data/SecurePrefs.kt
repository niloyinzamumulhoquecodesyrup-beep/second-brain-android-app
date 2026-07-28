package com.secondbrain.lock.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for the mobile auth session and the mirrored web theme choice ("sb_theme").
 * The theme key intentionally matches the web app's localStorage key so both are conceptually
 * the same setting — flipped from the native ThemeToggle in the app's top bar.
 */
object SecurePrefs {
    private const val FILE_NAME = "second_brain_secure_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_EMAIL = "auth_email"
    private const val KEY_THEME = "sb_theme"

    private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences = cached ?: synchronized(this) {
        cached ?: run {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context.applicationContext,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { cached = it }
        }
    }

    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    fun getEmail(context: Context): String? = prefs(context).getString(KEY_EMAIL, null)

    fun saveAuth(context: Context, email: String, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).putString(KEY_EMAIL, email).apply()
    }

    fun clearAuth(context: Context) {
        prefs(context).edit().remove(KEY_TOKEN).remove(KEY_EMAIL).apply()
    }

    /** "dark" or "light" — defaults to "dark" to match the web app's default. */
    fun getTheme(context: Context): String = prefs(context).getString(KEY_THEME, "dark") ?: "dark"

    fun setTheme(context: Context, theme: String) {
        prefs(context).edit().putString(KEY_THEME, theme).apply()
    }
}
