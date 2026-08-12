package com.secondbrain.lock.service

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.secondbrain.lock.data.AppDatabase
import com.secondbrain.lock.data.SecurePrefs
import com.secondbrain.lock.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * FCM backstop tier (P5) — fires when the local AlarmManager tier ([ReminderScheduler], P2) can't:
 * the process was killed by an OEM battery manager, the exact-alarm permission is denied and the
 * inexact fallback's window hasn't elapsed yet, etc. This is deliberately the SAME construction
 * path as the local tier — [ReminderNotifier.show] — so there's exactly one place a reminder
 * notification is ever built, and its notification id (a stable hash of reminderId) means a
 * reminder that arrives via both tiers replaces rather than duplicates.
 *
 * CRITICAL: the server must send DATA-ONLY messages, never a `notification` payload. A
 * `notification` payload is rendered directly by the system and completely bypasses our channels,
 * the P4 escalation ladder, and the P3 action buttons. If a future backend change starts including
 * a notification block, [onMessageReceived] below simply won't fire for those messages — that
 * would be the bug to chase, not something to "fix" here.
 */
class SbMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // No point registering a token against no account — the next login (or LockApp's
        // once-per-cold-start check below) picks up whatever token exists by then.
        if (SecurePrefs.getToken(applicationContext) == null) return
        CoroutineScope(Dispatchers.IO).launch {
            registerIfChanged(applicationContext, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val reminderId = data["reminder_id"] ?: return
        val taskId = data["task_id"]
        val title = data["title"] ?: "Task reminder"
        val body = data["body"] ?: "Scheduled for right now."

        CoroutineScope(Dispatchers.IO).launch {
            // Same lookup ReminderAlarmReceiver does — a reminder that's already been snoozed a
            // few times and then arrives via this backstop tier instead of its own re-scheduled
            // alarm must still show the escalated presentation, not silently reset to level 0.
            val snoozeCount = AppDatabase.get(applicationContext).reminderStateDao().get(reminderId)?.snoozeCount ?: 0
            ReminderNotifier.show(
                context = applicationContext,
                reminderId = reminderId,
                taskId = taskId,
                title = title,
                body = body,
                snoozeCount = snoozeCount
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "fcm_prefs"
        private const val KEY_LAST_SENT_TOKEN = "last_sent_token"

        /** Called from [onNewToken], and once per cold start from [com.secondbrain.lock.LockApp] —
         * the token can exist before the user ever logs in (nothing to register against yet) or can
         * simply never have changed since the last successful send, so this is a cheap local check
         * before ever hitting the network. */
        suspend fun registerIfChanged(context: Context, token: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LAST_SENT_TOKEN, null) == token) return
            ApiClient.registerDeviceToken(token).onSuccess {
                prefs.edit().putString(KEY_LAST_SENT_TOKEN, token).apply()
            }
        }

        /** Forgets the locally-remembered "last sent" token (called on logout, alongside the actual
         * server-side unregister) so a different account logging in next on this same device isn't
         * skipped by the unchanged-token check above. */
        fun forgetSentToken(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_LAST_SENT_TOKEN).apply()
        }

        /** Single entry point for "fetch whatever the current FCM token is, and register it if it's
         * new" — used from [com.secondbrain.lock.LockApp]'s cold-start check and from both login
         * success paths in [com.secondbrain.lock.MainActivity]. */
        suspend fun fetchAndRegisterCurrentToken(context: Context) {
            val token = currentToken() ?: return
            registerIfChanged(context, token)
        }

        /** Logout counterpart to [fetchAndRegisterCurrentToken] — called from [com.secondbrain.lock.MainActivity]'s
         * `onLogout` while the auth token is still valid (an unauthenticated DELETE would just
         * 401), best-effort like the sync-queue flush it runs alongside. */
        suspend fun unregisterCurrentToken(context: Context) {
            val token = currentToken() ?: return
            runCatching { ApiClient.unregisterDeviceToken(token) }
            forgetSentToken(context)
        }

        /** Bridges [FirebaseMessaging]'s Play-Services `Task` callback into a suspend call by hand
         * rather than pulling in `kotlinx-coroutines-play-services` for what would otherwise be its
         * only use in the app. */
        private suspend fun currentToken(): String? = runCatching {
            suspendCancellableCoroutine<String> { cont ->
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        }.getOrNull()
    }
}
