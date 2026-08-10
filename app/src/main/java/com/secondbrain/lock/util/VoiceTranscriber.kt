package com.secondbrain.lock.util

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.secondbrain.lock.data.FeedbackUtil
import com.secondbrain.lock.data.MorningBriefRequest
import com.secondbrain.lock.data.repo.NotesRepository
import com.secondbrain.lock.data.repo.TasksRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.VoiceClassifyResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** How long a pause has to last before the recognizer treats the sentence as finished — long
 * enough to survive a breath or "umm" mid-thought, short enough that it doesn't feel stuck. */
private const val SilencePauseMs = 2000

/** Effectively "never" for [sticky] sessions — a long-press should stay open through silence
 * (thinking pauses, background noise) rather than auto-submitting the way a tap does. */
private const val StickySilencePauseMs = Int.MAX_VALUE

/** [VoiceClassification.type] value the backend returns for "play my briefing" / "what's my daily
 * briefing" style commands — see [VoiceTranscriber.classify]'s doc for why this needs to be a
 * server-side classification rather than a client-side keyword match. */
private const val PlayBriefingType = "play_briefing"

/**
 * Wraps [SpeechRecognizer] for the shield button's tap-to-talk voice capture. A singleton because
 * only one listening session is ever active, and the trigger (BottomBar's tap), the full-screen
 * overlay, and the button's own processing animation all need to observe the same state without a
 * controller instance being threaded between them.
 *
 * The button only *starts* the session — [start] is a fire-and-forget trigger, not something the
 * caller holds open. Once running, the recognizer keeps listening through pauses/breaths and
 * decides for itself when the user is done talking (a real [SilencePauseMs] gap in speech), at
 * which point it submits automatically via [finishListening]. That's what lets a tap work as "tap
 * once, keep listening until you stop talking" instead of requiring a held button. A long-press
 * starts the same session in [sticky][start] mode instead, which stays open through silence too —
 * for when the user wants to think mid-capture without the session cutting them off.
 */
object VoiceTranscriber {
    var isListening by mutableStateOf(false)
        private set
    var transcript by mutableStateOf("")
        private set
    var isProcessing by mutableStateOf(false)
        private set

    private var recognizer: SpeechRecognizer? = null
    private var appContext: Context? = null

    // RecognitionListener callbacks aren't suspend functions, but classify() does network I/O —
    // this is what lets finishListening() launch it directly from onResults/onError.
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) = finishListening()
        override fun onResults(results: Bundle?) {
            applyResults(results)
            finishListening()
        }
        override fun onPartialResults(partialResults: Bundle?) = applyResults(partialResults)
        override fun onEvent(eventType: Int, params: Bundle?) {}

        private fun applyResults(bundle: Bundle?) {
            // A final onResults after the user goes quiet can carry an empty string (Android's
            // way of saying "nothing new") — applying it blindly would stomp the good partial
            // text already captured, leaving classify() with nothing to send.
            val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) transcript = text
        }
    }

    /** @param sticky When true (the shield button's long-press), the recognizer ignores silence
     * gaps entirely instead of auto-submitting after [SilencePauseMs] — the session stays open
     * until [stop] is called explicitly (a tap while already listening). */
    fun start(context: Context, sticky: Boolean = false) {
        if (isListening || !SpeechRecognizer.isRecognitionAvailable(context)) return
        transcript = ""
        isListening = true
        appContext = context.applicationContext
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(listener)
        val silenceMs = if (sticky) StickySilencePauseMs else SilencePauseMs
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Only a full silenceMs of quiet after speech ends the session — the default
                // (~1s on most devices) was cutting sentences off mid-thought.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
            }
        )
    }

    /** Manual early stop/cancel — the tap-to-stop gesture on an already-listening session (mainly
     * how a [sticky][start] long-press session ever ends, since it ignores silence on its own).
     * Only requests the wind-down; [recognizer]'s own onResults/onError callback — fired
     * asynchronously once it finishes packaging up whatever was captured — is what actually tears
     * the session down via [finishListening] and submits it. Destroying the recognizer here
     * instead (as this used to) raced that callback and killed it before it could ever fire,
     * silently dropping the capture. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** The recognizer itself decided the user is done talking (a real result, or a no-speech/
     * timeout error) — tear down the session and submit whatever was captured. */
    private fun finishListening() {
        val context = appContext
        teardown()
        if (context != null) {
            scope.launch { classify(context) }
        }
    }

    /** Sends the just-captured [transcript] to the backend for classification into a task or PARA
     * capture. Normally triggered automatically by [finishListening]; exposed publicly too since
     * nothing about it depends on how the session ended.
     *
     * One classification type is handled entirely on-device: [PlayBriefingType] means the backend
     * decided this was "play my briefing" / "what's my daily briefing" rather than a task/note to
     * create — the LLM classifier is what tells those apart from a sentence that merely *mentions*
     * a briefing ("I have a daily briefing today at 10 o'clock" still classifies as a normal task),
     * which a client-side keyword match couldn't reliably do. No `record` row exists for this type,
     * so it skips the task/note refresh and instead plays whatever's cached for today. */
    suspend fun classify(context: Context): Result<VoiceClassifyResponse> {
        val text = transcript.trim()
        if (text.isEmpty()) return Result.failure(IllegalStateException("Nothing was transcribed"))
        isProcessing = true
        return try {
            ApiClient.classifyVoice(text).also { result ->
                result.onSuccess { response ->
                    if (response.classification.type == PlayBriefingType) {
                        playCachedBriefing(context)
                    } else {
                        FeedbackUtil.successDing(context)
                        // The created row is either a task or a PARA note — refresh both rather than
                        // branching further on classification.type, so whichever screen the user
                        // checks next (Work or Organize) already shows it instead of needing an app
                        // restart.
                        TasksRepository.refresh()
                        NotesRepository.refreshPara()
                    }
                }
            }
        } finally {
            isProcessing = false
        }
    }

    private var briefingPlayer: MediaPlayer? = null

    /** Fetches whatever's already been generated for today and plays it — never triggers generation
     * itself (same read-only GET the POST-timeout fallback uses), so a "nothing yet" 404 is a normal
     * outcome here, not an error worth alarming over. No ding/haptic on success — the narration
     * starting is itself the feedback, and layering a chime under speech would just sound off.
     *
     * If [MorningBriefSection][com.secondbrain.lock.ui.screens.work.MorningBriefSection] is
     * currently mounted (the user is on the Work tab), this defers to it entirely via
     * [MorningBriefRequest.trigger] instead — that resurfaces the actual briefing card/animation
     * rather than just playing audio with nothing on screen. Off that tab, there's no UI to
     * resurface, so this headless path is what plays it. */
    private suspend fun playCachedBriefing(context: Context) {
        if (MorningBriefRequest.isMounted) {
            MorningBriefRequest.trigger()
            return
        }
        val briefing = ApiClient.getCachedBriefing().getOrNull()
        if (briefing == null) {
            Toast.makeText(context, "No briefing ready yet today", Toast.LENGTH_SHORT).show()
            return
        }
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Base64.decode(briefing.audio.data, Base64.DEFAULT)
                File(context.cacheDir, "voice_briefing.wav").apply { writeBytes(bytes) }
            }
        }.getOrNull() ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                briefingPlayer?.release()
                val mp = MediaPlayer()
                mp.setDataSource(file.absolutePath)
                mp.setOnCompletionListener { it.release() }
                mp.setOnErrorListener { p, _, _ -> p.release(); true }
                mp.prepare()
                mp.start()
                briefingPlayer = mp
            }
        }
    }

    private fun teardown() {
        recognizer?.destroy()
        recognizer = null
        isListening = false
    }
}
