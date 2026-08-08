package com.secondbrain.lock.data

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

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

    /** Shield button's voice capture starting to listen — a short, higher "ding". Distinct from
     * [voiceStop] (recording ending) and [successDing] (the backend finishing up), so all three
     * stages of one capture read as three different events rather than the same beep three times. */
    fun voiceStart(context: Context) {
        if (SoundHapticsPrefs.isSoundsEnabled(context)) {
            runCatching { toneGenerator()?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
        }
    }

    /** Shield button's voice capture ending — a lower "dong" once the recognizer detects the
     * silence pause and stops listening, right before the transcript heads off to the backend. */
    fun voiceStop(context: Context) {
        if (SoundHapticsPrefs.isSoundsEnabled(context)) {
            runCatching { toneGenerator()?.startTone(ToneGenerator.TONE_PROP_ACK, 150) }
        }
    }

    /** A single "ding" once a backgrounded request finishes successfully (e.g. the shield button's
     * voice capture being classified) — a longer, higher tone than [spinTick]'s rapid-fire beep so
     * it reads as a distinct "done" cue rather than another tick.
     *
     * Deliberately its own short-lived [ToneGenerator] on STREAM_NOTIFICATION rather than the
     * shared STREAM_SYSTEM instance every other cue here uses — on at least one OEM skin (ColorOS)
     * STREAM_SYSTEM tones fired outside a direct touch callback (this one fires after an async
     * network response, not from the tap itself) were confirmed reaching this code but producing
     * no audible sound, while [FocusPomodoro][com.secondbrain.lock.ui.screens.work.FocusPomodoro]'s
     * near-identical "session complete" cue already relies on this exact STREAM_NOTIFICATION
     * pattern successfully. */
    fun successDing(context: Context) {
        if (!SoundHapticsPrefs.isSoundsEnabled(context)) return
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed({ runCatching { tg.release() } }, 350L)
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
