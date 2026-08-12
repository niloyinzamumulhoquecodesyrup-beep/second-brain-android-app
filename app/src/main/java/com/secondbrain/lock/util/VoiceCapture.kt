package com.secondbrain.lock.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Thin wrapper around [SpeechRecognizer] for [com.secondbrain.lock.ui.nav.FastCaptureSheet]'s mic
 * button (P12). One instance per sheet (create via `remember { VoiceCapture(context) }`), not a
 * global singleton — only one sheet is ever open at a time, so there's nothing to share. Both the
 * sheet's own mic tap AND the bottom bar's long-press-to-speak entry (P6a) call [start] on this
 * same instance rather than each having their own listening implementation.
 *
 * [SpeechRecognizer] must be created and driven from the main thread — this class assumes it's
 * only ever touched from Compose callbacks, which already run there.
 */
class VoiceCapture(private val context: Context) {
    var isListening by mutableStateOf(false)
        private set

    /** Streams live as speech is recognized — [FastCaptureSheet] renders this distinctly
     * (Mist300, italic) from settled/typed text, so the user sees words appear as they speak
     * rather than everything landing at once when they stop. */
    var partialText by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null

    fun start(onFinalResult: (String) -> Unit, onError: () -> Unit) {
        if (isListening) return
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        this.recognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                isListening = false
                partialText = ""
                cleanup()
                onError()
            }

            override fun onResults(results: Bundle?) {
                val finalText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                isListening = false
                partialText = ""
                cleanup()
                if (finalText.isNotBlank()) onFinalResult(finalText)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            // Auto-stop after 1.5s of silence, per spec — both the "definitely done" and
            // "probably done" thresholds, so a brief pause mid-thought doesn't cut it off early
            // but a real stop doesn't linger waiting for more.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Some OEMs route recognition through a cloud service that fails silently with
                // no connection — prefer on-device where the platform supports the distinction.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        isListening = true
        partialText = ""
        recognizer.startListening(intent)
    }

    /** Ends listening normally — the recognizer's own `onResults` still fires with whatever it
     * caught, same as the auto-stop-on-silence path. Used when the user dismisses/saves the sheet
     * while still listening, so nothing they already said is thrown away. */
    fun stop() {
        recognizer?.stopListening()
    }

    private fun cleanup() {
        recognizer?.destroy()
        recognizer = null
    }
}
