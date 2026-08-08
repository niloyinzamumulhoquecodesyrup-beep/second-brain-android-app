package com.secondbrain.lock.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.util.VoiceTranscriber
import kotlinx.coroutines.delay

/** How long the transcript stays on screen after release before the overlay clears — long enough
 * to read what was just captured as it hands off to the button's processing animation. */
private const val LingerAfterReleaseMs = 1000L

/**
 * Full-screen scrim shown while [VoiceTranscriber] is listening from the shield button's
 * long-press gesture — dims everything behind it and surfaces the live partial transcript.
 * Consumes touches itself so nothing behind it reacts while the mic is live. Stays up for a beat
 * after release (showing the frozen final transcript) instead of vanishing the instant listening
 * stops, since that used to cut the text off right as the user let go.
 */
@Composable
fun VoiceListenOverlay() {
    var visible by remember { mutableStateOf(VoiceTranscriber.isListening) }
    var frozenTranscript by remember { mutableStateOf(VoiceTranscriber.transcript) }

    LaunchedEffect(VoiceTranscriber.isListening) {
        if (VoiceTranscriber.isListening) {
            visible = true
        } else {
            frozenTranscript = VoiceTranscriber.transcript
            delay(LingerAfterReleaseMs)
            visible = false
        }
    }

    if (!visible) return

    val displayText = if (VoiceTranscriber.isListening) VoiceTranscriber.transcript else frozenTranscript

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText.ifBlank { "Listening…" },
            color = Color.White,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
    }
}
