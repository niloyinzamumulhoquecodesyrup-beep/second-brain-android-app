package com.secondbrain.lock.ui.nudge

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.secondbrain.lock.MainActivity
import com.secondbrain.lock.data.FeedbackUtil
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.receiver.ReminderActionReceiver
import com.secondbrain.lock.service.ReminderScheduler
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.SecondBrainLockTheme
import com.secondbrain.lock.ui.theme.StreakAccent
import kotlinx.coroutines.launch

/**
 * The level-3 escalation destination ([com.secondbrain.lock.service.ReminderNotifier]'s
 * `setFullScreenIntent`) — shown over the lock screen the same way
 * [com.secondbrain.lock.ui.wake.WakeFlowActivity] shows the wake alarm. Exactly three doors and
 * no fourth: no close button, no back-dismiss. "Not today" must be genuinely free — it neither
 * increments the snooze count nor schedules a follow-up, unlike every other exit from this
 * reminder.
 */
class NudgeTakeoverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no back-dismiss — one of the three buttons only */ }
        })

        val reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: run { finish(); return }
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Task reminder"

        setContent {
            SecondBrainLockTheme {
                NudgeTakeoverScreen(
                    title = title,
                    onDoItNow = { doItNow(reminderId, taskId) },
                    onBreakItDown = { breakItDown(reminderId, taskId, title) },
                    onNotToday = { notToday(reminderId) }
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
    }

    /** Starts the real 2-minute session server-side via the same cross-device
     * `POST /api/focus/state` every other client uses — [com.secondbrain.lock.service.MonitorService]'s
     * poll picks it up from there, same as any other focus start. Full in-app navigation straight
     * into [com.secondbrain.lock.ui.screens.work.FocusPomodoro] (P14) needs deep-link plumbing this
     * app doesn't have yet (that's P7's job); for now this brings the user into the app with the
     * session already running rather than half-building a parallel entry point. */
    private fun doItNow(reminderId: String, taskId: String?) {
        FeedbackUtil.longPressTick(this)
        lifecycleScope.launch {
            runCatching { ApiClient.startFocusSession(minutes = 2, taskId = taskId, mode = "micro") }
            cancelNotification(reminderId)
            openApp()
        }
    }

    /** Routes through [ReminderActionReceiver.ACTION_BREAK] via an explicit broadcast rather than
     * duplicating its breakdown-and-repost logic here — same reasoning as the notification's own
     * "Too big" button, just triggered from this screen instead of the shade. Sent from this app's
     * own process, so [ReminderActionReceiver]'s `exported="false"` doesn't block it (unlike a
     * plain `adb shell` broadcast from outside the app — see the receiver's own KDoc). */
    private fun breakItDown(reminderId: String, taskId: String?, title: String) {
        FeedbackUtil.longPressTick(this)
        sendBroadcast(
            Intent(this, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_BREAK
                putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
                putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId ?: reminderId)
                putExtra(ReminderScheduler.EXTRA_TITLE, title)
            }
        )
        finish()
    }

    private fun notToday(reminderId: String) {
        cancelNotification(reminderId)
        finish()
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        finish()
    }

    private fun cancelNotification(reminderId: String) {
        getSystemService(NotificationManager::class.java)?.cancel(reminderId.hashCode())
    }

    companion object {
        fun intent(context: Context, reminderId: String, taskId: String?, title: String): Intent =
            Intent(context, NudgeTakeoverActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
                putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
                putExtra(ReminderScheduler.EXTRA_TITLE, title)
            }
    }
}

@Composable
private fun NudgeTakeoverScreen(
    title: String,
    onDoItNow: () -> Unit,
    onBreakItDown: () -> Unit,
    onNotToday: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Ink950).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Mist100,
                textAlign = TextAlign.Center,
                maxLines = 3
            )

            Button(
                onClick = onDoItNow,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White)
            ) {
                Text("Do it now — 2 minutes", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedButton(
                onClick = onBreakItDown,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mist100)
            ) {
                Text("Break it down", style = MaterialTheme.typography.bodyMedium)
            }

            TextButton(onClick = onNotToday) {
                Text("Not today, and that's fine", color = Mist300, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
