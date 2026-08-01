package com.secondbrain.lock.ui.screens.mindverse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.data.repo.MindverseRepository
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink700
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit

/** "8d ago" / "3h ago" / "just now" — same granularity as the web's community timestamps. */
internal fun relativeTime(createdAt: String?): String {
    val instant = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ""
    val minutes = ChronoUnit.MINUTES.between(instant, Instant.now())
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}

@Composable
internal fun AvatarBadge(avatarKey: String, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Ink700),
        contentAlignment = Alignment.Center
    ) { Text(avatarKey.ifBlank { "🙂" }, fontSize = (size.value * 0.5).sp) }
}

@Composable
internal fun CommunityRow(avatarKey: String, displayName: String, createdAt: String?, body: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        AvatarBadge(avatarKey)
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Row {
                Text(displayName, color = Mist300, style = SecondBrainTypography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Text(relativeTime(createdAt), color = Mist400, fontSize = 10.sp)
            }
            Spacer(Modifier.width(2.dp))
            Text(body, color = Mist100, style = SecondBrainTypography.bodyMedium)
        }
    }
}

@Composable
internal fun ChatComposer(
    placeholder: String = "Say something",
    maxLength: Int = 500,
    showCounter: Boolean = false,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= maxLength) text = it },
            placeholder = { Text(placeholder, color = Mist400) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = StreakAccent.copy(alpha = 0.6f),
                unfocusedBorderColor = Ink500,
                focusedTextColor = Mist100,
                unfocusedTextColor = Mist100,
                cursorColor = StreakAccent
            )
        )
        if (showCounter) {
            Spacer(Modifier.width(6.dp))
            Text("${text.length}/$maxLength", color = Mist400, fontSize = 10.sp)
        }
        IconButton(onClick = {
            val trimmed = text.trim()
            if (trimmed.isNotBlank()) {
                text = ""
                onSend(trimmed)
            }
        }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = StreakAccent) }
    }
}

/** Shows an inline error near a composer and auto-clears it a few seconds later or as soon as
 * a new one replaces it — matches the web's per-widget inline error text. [onDismiss] defaults
 * to clearing the shared [MindverseRepository.error] signal; callers tracking their own local
 * error state (so one widget's failure doesn't blank another's) pass their own clear action. */
@Composable
internal fun ErrorLine(error: String?, onDismiss: () -> Unit = { MindverseRepository.clearError() }) {
    LaunchedEffect(error) {
        if (error != null) {
            delay(4000)
            onDismiss()
        }
    }
    if (error != null) {
        Spacer(Modifier.height(6.dp))
        Text(error, color = Rose400, style = SecondBrainTypography.bodySmall)
    }
}
