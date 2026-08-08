package com.secondbrain.lock.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.util.VoiceTranscriber
import kotlinx.coroutines.delay

/** How long the bubble lingers after listening stops, showing the frozen final transcript, before
 * disappearing — long enough to read what was just captured as it hands off to the button's own
 * processing animation. */
private const val LingerAfterReleaseMs = 1000L

/**
 * Small floating "glass" card that surfaces the live partial transcript while [VoiceTranscriber]
 * is listening. Replaces the old full-screen dimming scrim — that blocked the shield button
 * underneath it (needing either a duplicate button or a hole punched in the scrim to work around)
 * for the sake of showing a line of text. This is sized to the text instead of the whole screen,
 * floats above the bottom bar rather than covering it, and doesn't consume touches, so nothing
 * else needs to work around it.
 *
 * The frosted look is a dark translucent base (for legible white text over whatever's behind it,
 * light or dark screens alike) plus a subtle white gradient sheen and a hairline border for the
 * glass edge — an approximation, not a true backdrop blur (which would need Android 12+'s
 * RenderEffect and a snapshot of whatever's behind), but reads the same at this size.
 */
@Composable
fun VoiceTranscriptBubble(modifier: Modifier = Modifier) {
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
    val shape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .widthIn(max = 320.dp)
            .shadow(elevation = 12.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.4f))
            .clip(shape)
            // Dark base first (guarantees contrast for the white text regardless of what's behind
            // it), then a faint white gradient on top for the glass "sheen".
            .background(Color.Black.copy(alpha = 0.55f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)))
            .border(1.dp, Color.White.copy(alpha = 0.30f), shape)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = displayText.ifBlank { "Listening…" },
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}
