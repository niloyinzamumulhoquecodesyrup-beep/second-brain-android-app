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
            val vibrator = vibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { vibrator.vibrate(VibrationEffect.createOneShot(8, 60)) }
            } else {
                @Suppress("DEPRECATION")
                runCatching { vibrator.vibrate(8) }
            }
        }
    }
}
