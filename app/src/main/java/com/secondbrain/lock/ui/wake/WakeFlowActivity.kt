package com.secondbrain.lock.ui.wake

import android.app.KeyguardManager
import android.app.TimePickerDialog
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import com.secondbrain.lock.R
import java.util.Calendar
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.secondbrain.lock.data.SleepPrefs
import com.secondbrain.lock.service.AlarmScheduler
import com.secondbrain.lock.ui.screens.JournalScreen
import com.secondbrain.lock.ui.theme.SecondBrainLockTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The whole wake experience lives in one Activity, moving through three calm phases rather than
 * separate screens: ring -> a brief wellbeing prompt (blocks until acknowledged, but always
 * escapable) -> an optional journal nudge. Tone stays warm throughout — see WELLBEING_COPY.
 */
class WakeFlowActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var phase by mutableStateOf(WakePhase.RING)
    private var writingJournal by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        startRinging()

        setContent {
            SecondBrainLockTheme {
                if (writingJournal) {
                    JournalScreen(
                        onDone = { finish() },
                        onSkip = { finish() }
                    )
                } else {
                    WakeFlowScreen(
                        phase = phase,
                        canSnooze = SleepPrefs.canSnoozeToday(this),
                        canSkipWellbeing = SleepPrefs.canSkipToday(this),
                        onStopAlarm = {
                            stopRinging()
                            // The 1-hour "block every other app" window starts the moment the
                            // alarm is silenced, not when wellbeing is eventually marked done.
                            SleepPrefs.startMorningRoutine(this, 60)
                            phase = WakePhase.WELLBEING
                        },
                        onSnooze = {
                            stopRinging()
                            SleepPrefs.consumeSnooze(this)
                            AlarmScheduler.scheduleSnooze(this, 10)
                            finish()
                        },
                        onSetAlarmForLater = { pickLaterAlarm() },
                        onEmergencyCall = { runCatching { startActivity(Intent(Intent.ACTION_DIAL)) } },
                        onWellbeingDone = {
                            SleepPrefs.endMorningRoutine(this)
                            phase = WakePhase.JOURNAL
                        },
                        onSkipWellbeing = {
                            SleepPrefs.consumeSkip(this)
                            SleepPrefs.endMorningRoutine(this)
                            phase = WakePhase.JOURNAL
                        },
                        onShowLater = {
                            SleepPrefs.endMorningRoutine(this)
                            AlarmScheduler.scheduleSnooze(this, 10)
                            finish()
                        },
                        onWriteJournal = { writingJournal = true },
                        onRemindInWebApp = {
                            SleepPrefs.setJournalReminderPending(this, true)
                            finish()
                        },
                        onDismissWithoutJournal = { finish() }
                    )
                }
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
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        keyguardManager?.requestDismissKeyguard(this, null)
    }

    private fun startRinging() {
        // A bundled tone we control end-to-end, looped via MediaPlayer, so the alarm reliably
        // makes sound regardless of the device's default-alarm-ringtone setting (which can be
        // unset, silent, or missing on some devices/profiles). Falls back to the system default
        // alarm ringtone only if the bundled asset somehow fails to load.
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val started = runCatching {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(attributes)
                isLooping = true
                resources.openRawResourceFd(R.raw.alarm_wake).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                prepare()
                start()
            }
        }.isSuccess
        if (!started) {
            mediaPlayer?.release()
            mediaPlayer = null
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = attributes
                }
                play()
            }
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 500, 500)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
        }
    }

    /** "Put alarm for later" — a plain time picker; picking a time reschedules the one-off
     * alarm and dismisses this flow entirely, same as snoozing. */
    private fun pickLaterAlarm() {
        val now = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                stopRinging()
                AlarmScheduler.scheduleAt(this, hourOfDay, minute)
                finish()
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun stopRinging() {
        runCatching { mediaPlayer?.stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        ringtone?.stop()
        vibrator?.cancel()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    companion object {
        /** Kicked off once, right after onboarding/settings, so a stale one-day-old alarm never lingers. */
        fun rescheduleAsync(context: android.content.Context) {
            CoroutineScope(Dispatchers.Default).launch { AlarmScheduler.reschedule(context) }
        }
    }
}

enum class WakePhase { RING, WELLBEING, JOURNAL }
