package com.secondbrain.lock.ui.screens.work

import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.R
import com.secondbrain.lock.data.MorningBriefPrefs
import com.secondbrain.lock.data.MorningBriefRequest
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.BriefingResponse
import com.secondbrain.lock.ui.theme.StreakAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalTime
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private enum class BriefState { LOADING, PLAYING, NOT_READY, DONE }

/** Briefings don't exist yet for "today" until the server rolls its day over — matches the app's
 * own local-day boundary rather than the server's UTC one, close enough for a once-a-day nicety. */
private const val EARLIEST_HOUR = 3

/** How long to wait after a timed-out/dropped POST before trying the read-only GET fallback —
 * generation can still be running server-side well past the POST's own timeout, so checking
 * immediately would almost always just re-hit the same "not ready yet" 404. A full minute gives
 * that background work real room to finish before we give up for the day. */
private const val PostFailureRetryDelayMs = 60_000L

/**
 * Ambient, text-free audio briefing card — shown above every other section on [WorkScreen], any
 * time after [EARLIEST_HOUR] local time, once per day ([MorningBriefPrefs]). No wake-alarm/routine
 * dependency — the first app-open past 3am each day is the trigger. Fetches
 * [ApiClient.getTodayBriefing], plays the WAV it returns, and collapses out of the layout the
 * moment playback finishes.
 *
 * Also resurfaces on demand: the "play my daily briefing" voice command bumps
 * [MorningBriefRequest.id] while this section is mounted, which replays whatever's already cached
 * (a plain GET, no POST/wait — the ambient trigger above already owns first-generation duty).
 */
@Composable
fun MorningBriefSection() {
    val context = LocalContext.current
    val ambientEligible = remember {
        LocalTime.now().hour >= EARLIEST_HOUR && !MorningBriefPrefs.hasPlayedToday(context)
    }

    var state by remember { mutableStateOf(if (ambientEligible) BriefState.LOADING else BriefState.DONE) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var visualizer by remember { mutableStateOf<Visualizer?>(null) }
    // Real-time playback loudness (0..1) from Visualizer's waveform capture — drives the icon's
    // scale directly, no synthetic/timed animation standing in for it.
    var amplitude by remember { mutableFloatStateOf(0f) }

    // Tears down whatever's playing (if anything) and hides the card — shared by natural
    // completion/error inside [playBriefing] and by the card's own close button, so either path
    // leaves playback resources cleanly released and today's "played" flag set the same way.
    // Explicitly stopping+releasing the player (rather than leaving it for DisposableEffect's
    // eventual onDispose) matters here specifically because both the close button and a voice
    // replay can interrupt live playback — without it, audio would keep going underneath.
    val finish: () -> Unit = {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { visualizer?.release() }
        visualizer = null
        amplitude = 0f
        MorningBriefPrefs.markPlayedToday(context)
        state = BriefState.DONE
    }

    // Decodes and plays [briefing]'s audio, wiring the same LOADING→PLAYING→DONE transitions
    // regardless of whether the fetch that produced it was the ambient once-a-day one or a voice
    // replay. Interrupts and replaces whatever's currently playing, if anything, so the two sources
    // can never end up with two MediaPlayers running at once.
    suspend fun playBriefing(briefing: BriefingResponse) {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { visualizer?.release() }
        visualizer = null
        amplitude = 0f
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
            return
        }
        val mp = MediaPlayer()
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

    LaunchedEffect(Unit) {
        if (!ambientEligible) return@LaunchedEffect
        val result = ApiClient.getTodayBriefing(currentTimeMin = currentMinuteOfDay())
        // A timed-out/dropped POST doesn't mean generation failed — it's likely still running
        // server-side well after our connection gave up. Wait before the read-only GET check so
        // that background work has a real chance to finish instead of just re-hitting the same
        // "not ready yet" 404 immediately; the GET itself can't trigger a duplicate generation, so
        // there's no harm trying even when the POST failed for an unrelated reason.
        val briefing = result.getOrNull() ?: run {
            delay(PostFailureRetryDelayMs)
            ApiClient.getCachedBriefing().getOrNull()
        }
        if (briefing == null) {
            if (com.secondbrain.lock.BuildConfig.DEBUG) {
                android.util.Log.w("MorningBrief", "fetch failed", result.exceptionOrNull())
            }
            // Ambient feature, no error UI — leave "played" unset so the window's next app-open retries.
            state = BriefState.DONE
            return@LaunchedEffect
        }
        playBriefing(briefing)
    }

    LaunchedEffect(MorningBriefRequest.id) {
        // id starts at 0 and this effect fires once on first composition regardless — skip that
        // initial run so mounting the section doesn't itself count as a replay request.
        if (MorningBriefRequest.id == 0) return@LaunchedEffect
        state = BriefState.LOADING
        val briefing = ApiClient.getCachedBriefing().getOrNull()
        if (briefing == null) {
            // Unlike the ambient trigger's silent failure above, this was an explicit "play my
            // briefing" ask — it deserves visible feedback on the card itself rather than vanishing
            // without a trace. Dismissing it here just hides the card (no finish()/markPlayedToday,
            // since nothing actually played) so the ambient trigger's own retry-tomorrow bookkeeping
            // is untouched.
            state = BriefState.NOT_READY
            return@LaunchedEffect
        }
        playBriefing(briefing)
    }

    DisposableEffect(Unit) {
        MorningBriefRequest.isMounted = true
        onDispose {
            MorningBriefRequest.isMounted = false
            runCatching { visualizer?.release() }
            player?.release()
        }
    }

    AnimatedVisibility(
        visible = state != BriefState.DONE,
        exit = fadeOut() + shrinkVertically()
    ) {
        MorningBriefCard(
            amplitude = if (state == BriefState.PLAYING) amplitude else 0f,
            loading = state == BriefState.LOADING,
            notReady = state == BriefState.NOT_READY,
            onDismiss = if (state == BriefState.NOT_READY) { { state = BriefState.DONE } } else finish
        )
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
private fun MorningBriefCard(amplitude: Float, loading: Boolean, notReady: Boolean, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color.Black)
            .border(1.5.dp, StreakAccent, CardShape)
    ) {
        StarField(modifier = Modifier.matchParentSize())

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.6f))
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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

            if (loading) {
                Row(modifier = Modifier.padding(top = 14.dp)) {
                    Text(
                        text = "Your morning brief is being prepared",
                        color = StreakAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    LoadingDots()
                }
            } else if (notReady) {
                Text(
                    text = "Today's briefing isn't ready yet",
                    color = StreakAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
    }
}

/** Trailing "..." for the loading label — each dot bobs up and down a beat after the last, so the
 * motion rolls left-to-right, while the sentence itself stays still and readable. */
@Composable
private fun LoadingDots() {
    val infinite = rememberInfiniteTransition(label = "briefLoadingDots")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "briefLoadingDotsPhase"
    )
    val amplitudePx = with(LocalDensity.current) { 3.dp.toPx() }

    Row {
        repeat(3) { index ->
            val y = sin(phase + index * 0.9f) * amplitudePx
            Text(
                text = ".",
                color = StreakAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer(translationY = y)
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
