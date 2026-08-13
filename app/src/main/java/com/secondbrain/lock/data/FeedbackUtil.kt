package com.secondbrain.lock.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.secondbrain.lock.R

/**
 * Short tick feedback for gesture UI (the PARA card-stack's spin), gated on [SoundHapticsPrefs].
 * The [ToneGenerator] and [Vibrator] are held for the app's lifetime — same tradeoff as a
 * keyboard's click-sound pool — since a fast spin can fire many ticks per second and per-call
 * allocation (like [WakeFlowActivity][com.secondbrain.lock.ui.wake.WakeFlowActivity]'s alarm
 * setup, which only ever fires once) would be wasteful here.
 */
object FeedbackUtil {
    private var toneGenerator: ToneGenerator? = null

    private fun toneGenerator(): ToneGenerator? {
        var tg = toneGenerator
        if (tg == null) {
            tg = runCatching { ToneGenerator(AudioManager.STREAM_SYSTEM, 35) }.getOrNull()
            toneGenerator = tg
        }
        return tg
    }

    private fun vibrator(context: Context): Vibrator? {
        val appContext = context.applicationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** A single short tick — sound and/or haptic, each independently toggleable in settings. */
    fun spinTick(context: Context) {
        if (SoundHapticsPrefs.isSoundsEnabled(context)) {
            runCatching { toneGenerator()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 20) }
        }
        if (SoundHapticsPrefs.isHapticsEnabled(context)) {
            vibrateOneShot(context, durationMs = 8, amplitude = 60)
        }
    }

    /** Haptic-only confirm for a discrete gesture (e.g. a long-press registering) — no tone, since
     * unlike [spinTick] this isn't a rapid-fire effect where sound reinforces the rhythm. */
    fun longPressTick(context: Context) {
        if (SoundHapticsPrefs.isHapticsEnabled(context)) {
            vibrateOneShot(context, durationMs = 15, amplitude = 90)
        }
    }

    /** P22: the double-thump, ascending — fires at the instant a task/routine/focus session is
     * marked complete, timed to land ~200ms before the celebration text so the haptic itself
     * reads as the reward, not just an acknowledgment. Never fired for a Shield lock triggering —
     * that's a block, not an achievement, and haptically celebrating it would be bizarre. */
    fun completionThump(context: Context) {
        if (!SoundHapticsPrefs.isHapticsEnabled(context)) return
        val vibrator = vibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 80), intArrayOf(0, 180, 0, 255), -1)
                )
            }
        }
    }

    /** A fresh level being reached is a bigger deal than an ordinary completion — a longer,
     * three-pulse ramp rather than the two-pulse [completionThump]. */
    fun levelUpThump(context: Context) {
        if (!SoundHapticsPrefs.isHapticsEnabled(context)) return
        val vibrator = vibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 30, 50, 30, 50, 60),
                        intArrayOf(0, 140, 0, 190, 0, 255),
                        -1
                    )
                )
            }
        }
    }

    /** Two short ascending tones — the sound half of a completion, gated independently of the
     * haptic so either channel can be turned off on its own. */
    fun completionChime(context: Context) {
        if (!SoundHapticsPrefs.isSoundsEnabled(context)) return
        runCatching {
            val tg = toneGenerator() ?: return@runCatching
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                { runCatching { tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 40) } },
                60
            )
        }
    }

    /** Plays once a backgrounded request finishes successfully (e.g. the shield button's voice
     * capture being classified into a new task).
     *
     * Deliberately USAGE_NOTIFICATION_EVENT rather than ASSISTANCE_SONIFICATION — on at least one
     * OEM skin (ColorOS) ASSISTANCE_SONIFICATION audio fired outside a direct touch callback (this
     * one fires after an async network response, not from the tap itself) was confirmed reaching
     * this code but producing no audible sound, while
     * [FocusPomodoro][com.secondbrain.lock.ui.screens.work.FocusPomodoro]'s near-identical "session
     * complete" cue already relies on this exact notification-stream pattern successfully. */
    fun successDing(context: Context) {
        playRaw(context, R.raw.notification_sound_3, AudioAttributes.USAGE_NOTIFICATION_EVENT)
    }

    // Fire-and-forget MediaPlayers must be kept reachable until they finish — a purely local
    // reference can be garbage-collected mid-playback, cutting the clip short.
    private val activePlayers = mutableSetOf<MediaPlayer>()

    /** Fire-and-forget playback of a [raw][R.raw] audio resource, released once playback completes. */
    private fun playRaw(context: Context, resId: Int, usage: Int) {
        if (!SoundHapticsPrefs.isSoundsEnabled(context)) return
        runCatching {
            val attributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val mp = MediaPlayer.create(context.applicationContext, resId, attributes, 0) ?: return@runCatching
            activePlayers += mp
            mp.setOnCompletionListener {
                activePlayers -= it
                it.release()
            }
            mp.start()
        }
    }

    private fun vibrateOneShot(context: Context, durationMs: Long, amplitude: Int) {
        val vibrator = vibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude)) }
        } else {
            @Suppress("DEPRECATION")
            runCatching { vibrator.vibrate(durationMs) }
        }
    }
}
