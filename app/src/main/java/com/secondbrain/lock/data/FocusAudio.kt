package com.secondbrain.lock.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.random.Random

enum class FocusAudioPreset { BROWN, PINK, RAIN }

/**
 * Generated background noise for focus sessions (P19). Three presets, no playlists, no
 * browsing — deliberately: browsing music is itself a procrastination surface.
 *
 * A ~10 second seamless buffer is generated ONCE per [start] call and looped via
 * [AudioTrack.MODE_STATIC] rather than streaming continuously for 25+ minutes — the battery cost
 * of streaming is measurable and a loop-point discontinuity is inaudible in noise.
 *
 * Lives at the object (process) level, not tied to any Composable's lifecycle, so playback
 * survives the focus screen closing when the app backgrounds — [MonitorService][com.secondbrain.lock.service.MonitorService]
 * being an always-running foreground service is what keeps the process (and this AudioTrack)
 * alive in the background; this object doesn't start a second foreground service of its own.
 */
object FocusAudio {
    private const val SAMPLE_RATE = 44_100
    private const val BUFFER_SECONDS = 10
    private const val BUFFER_FRAMES = SAMPLE_RATE * BUFFER_SECONDS
    private const val DUCK_VOLUME = 0.3f

    private var audioTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    val isPlaying: Boolean get() = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

    fun start(context: Context, preset: FocusAudioPreset) {
        stop()
        val am = context.applicationContext.getSystemService(AudioManager::class.java) ?: return
        audioManager = am
        if (!requestFocus(am)) return

        // No bundled seamless rain .ogg asset exists yet (P19 calls for one in res/raw, ~300KB) —
        // falls back to pink noise rather than silently doing nothing. Swap this branch for a
        // MediaPlayer/raw-asset loop once that asset is actually added to the project.
        val pcm = when (preset) {
            FocusAudioPreset.BROWN -> generateBrownNoise(BUFFER_FRAMES)
            FocusAudioPreset.PINK, FocusAudioPreset.RAIN -> generatePinkNoise(BUFFER_FRAMES)
        }

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull() ?: return

        track.write(pcm, 0, pcm.size)
        track.setLoopPoints(0, pcm.size, -1)
        track.play()
        audioTrack = track
    }

    fun stop() {
        audioTrack?.let { runCatching { it.stop(); it.release() } }
        audioTrack = null
        val am = audioManager
        val request = focusRequest
        if (am != null && request != null) am.abandonAudioFocusRequest(request)
        focusRequest = null
        audioManager = null
    }

    private fun requestFocus(am: AudioManager): Boolean {
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                // Duck rather than stop for a transient interruption (e.g. a notification sound)
                // — the whole point is unbroken background noise, not a full stop/restart cycle.
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> audioTrack?.setVolume(DUCK_VOLUME)
                AudioManager.AUDIOFOCUS_GAIN -> audioTrack?.setVolume(1f)
                // A genuine loss (another app took GAIN, e.g. music or the wake alarm) — stop
                // outright rather than duck; ducking here would still audibly fight the wake
                // alarm, which must always win.
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> stop()
            }
        }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setOnAudioFocusChangeListener(listener)
            .build()
        focusRequest = request
        return am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** Brown noise's power spectrum is white noise integrated over time (~-6dB/octave) —
     * approximated with a simple leaky integrator (low-pass filter) applied sample-by-sample. */
    private fun generateBrownNoise(frames: Int): ShortArray {
        val out = ShortArray(frames)
        var last = 0f
        for (i in 0 until frames) {
            val white = Random.nextFloat() * 2f - 1f
            last = (last + 0.02f * white) / 1.02f
            out[i] = (last * Short.MAX_VALUE * 3.5f).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /** Paul Kellet's cheap 3-pole approximation of pink noise (~-3dB/octave). */
    private fun generatePinkNoise(frames: Int): ShortArray {
        val out = ShortArray(frames)
        var b0 = 0f
        var b1 = 0f
        var b2 = 0f
        for (i in 0 until frames) {
            val white = Random.nextFloat() * 2f - 1f
            b0 = 0.99765f * b0 + white * 0.0990460f
            b1 = 0.96300f * b1 + white * 0.2965164f
            b2 = 0.57000f * b2 + white * 1.0526913f
            val pink = b0 + b1 + b2 + white * 0.1848f
            out[i] = (pink * Short.MAX_VALUE * 0.15f).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }
}
