package com.secondbrain.lock.ui.screens.work

import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.R
import com.secondbrain.lock.data.MorningBriefPrefs
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.ui.theme.StreakAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime
import kotlin.math.sqrt
import kotlin.random.Random

private enum class BriefState { LOADING, PLAYING, DONE }

/** Briefings don't exist yet for "today" until the server rolls its day over — matches the app's
 * own local-day boundary rather than the server's UTC one, close enough for a once-a-day nicety. */
private const val EARLIEST_HOUR = 3

/**
 * Ambient, text-free audio briefing card — shown above every other section on [WorkScreen], any
 * time after [EARLIEST_HOUR] local time, once per day ([MorningBriefPrefs]). No wake-alarm/routine
 * dependency — the first app-open past 3am each day is the trigger. Fetches
 * [ApiClient.getTodayBriefing], plays the WAV it returns, and collapses out of the layout the
 * moment playback finishes.
 */
@Composable
fun MorningBriefSection() {
    val context = LocalContext.current
    val eligible = remember {
        LocalTime.now().hour >= EARLIEST_HOUR && !MorningBriefPrefs.hasPlayedToday(context)
    }
    if (!eligible) return

    var state by remember { mutableStateOf(BriefState.LOADING) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var visualizer by remember { mutableStateOf<Visualizer?>(null) }
    // Real-time playback loudness (0..1) from Visualizer's waveform capture — drives the icon's
    // scale directly, no synthetic/timed animation standing in for it.
    var amplitude by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val result = ApiClient.getTodayBriefing()
        val briefing = result.getOrNull()
        if (briefing == null) {
            if (com.secondbrain.lock.BuildConfig.DEBUG) {
                android.util.Log.w("MorningBrief", "fetch failed", result.exceptionOrNull())
            }
            // Ambient feature, no error UI — leave "played" unset so the window's next app-open retries.
            state = BriefState.DONE
            return@LaunchedEffect
        }
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Base64.decode(briefing.audio.data, Base64.DEFAULT)
                File(context.cacheDir, "morning_brief.wav").apply { writeBytes(bytes) }
            }
        }.onFailure {
            if (com.secondbrain.lock.BuildConfig.DEBUG) android.util.Log.w("MorningBrief", "decode/write failed", it)
        }.getOrNull()
        if (file == null) {
            state = BriefState.DONE
            return@LaunchedEffect
        }
        val mp = MediaPlayer()
        val finish = {
            runCatching { visualizer?.release() }
            visualizer = null
            amplitude = 0f
            MorningBriefPrefs.markPlayedToday(context)
            state = BriefState.DONE
        }
        mp.setOnPreparedListener {
            state = BriefState.PLAYING
            it.start()
            visualizer = runCatching { attachVisualizer(it.audioSessionId) { level -> amplitude = level } }
                .getOrNull()
        }
        mp.setOnCompletionListener { finish() }
        mp.setOnErrorListener { _, _, _ -> finish(); true }
        runCatching {
            mp.setDataSource(file.absolutePath)
            mp.prepareAsync()
        }.onFailure { finish() }
        player = mp
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { visualizer?.release() }
            player?.release()
        }
    }

    AnimatedVisibility(
        visible = state != BriefState.DONE,
        exit = fadeOut() + shrinkVertically()
    ) {
        MorningBriefCard(amplitude = if (state == BriefState.PLAYING) amplitude else 0f)
    }
}

/** Attaches a [Visualizer] to [audioSessionId] and reports each waveform capture's RMS loudness
 * (roughly 0..1) via [onLevel]. Requires RECORD_AUDIO (already held for voice capture elsewhere). */
private fun attachVisualizer(audioSessionId: Int, onLevel: (Float) -> Unit): Visualizer {
    val visualizer = Visualizer(audioSessionId)
    val captureSize = Visualizer.getCaptureSizeRange()[0]
    visualizer.captureSize = captureSize
    visualizer.setDataCaptureListener(
        object : Visualizer.OnDataCaptureListener {
            override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                if (waveform == null || waveform.isEmpty()) return
                var sumSquares = 0.0
                for (b in waveform) {
                    val centered = (b.toInt() and 0xFF) - 128
                    sumSquares += (centered * centered).toDouble()
                }
                val rms = sqrt(sumSquares / waveform.size) / 128.0
                onLevel(rms.toFloat().coerceIn(0f, 1f))
            }

            override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
        },
        Visualizer.getMaxCaptureRate().coerceAtMost(20_000),
        true,
        false
    )
    visualizer.enabled = true
    return visualizer
}

private val CardShape = RoundedCornerShape(24.dp)

@Composable
private fun MorningBriefCard(amplitude: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color.Black)
            .border(1.5.dp, StreakAccent, CardShape)
    ) {
        StarField(modifier = Modifier.matchParentSize())

        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            var emerge by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { emerge = true }
            val emergeScale by animateFloatAsState(
                targetValue = if (emerge) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.6f),
                label = "emerge"
            )

            // Speaker-like pulse driven by the actual clip's loudness, smoothed just enough to
            // avoid per-sample jitter between Visualizer callbacks.
            val soundScale by animateFloatAsState(
                targetValue = 1f + (amplitude * 0.45f),
                animationSpec = tween(120),
                label = "soundScale"
            )
            val finalScale = emergeScale * soundScale

            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer(scaleX = finalScale, scaleY = finalScale)
            )
        }
    }
}

/** Static scatter of tiny stars across the card, seeded once per instance. */
@Composable
private fun StarField(modifier: Modifier = Modifier, count: Int = 36) {
    val stars = remember {
        val random = Random(System.nanoTime())
        List(count) {
            Triple(random.nextFloat(), random.nextFloat(), 0.6f + random.nextFloat() * 1.2f)
        }
    }
    Canvas(modifier = modifier) {
        stars.forEach { (xFrac, yFrac, radiusDp) ->
            drawCircle(
                color = Color.White.copy(alpha = 0.35f + (radiusDp / 1.8f) * 0.35f),
                radius = radiusDp.dp.toPx(),
                center = Offset(xFrac * size.width, yFrac * size.height)
            )
        }
    }
}
