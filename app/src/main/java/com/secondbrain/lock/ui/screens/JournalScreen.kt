package com.secondbrain.lock.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.fullAuraBackground
import kotlinx.coroutines.launch
import org.json.JSONObject

/** A minimal native note screen — posts straight to /api/notes, nothing more than the essentials. */
@Composable
fun JournalScreen(onDone: () -> Unit, onSkip: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fullAuraBackground()
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp)
    ) {
        SbLabel("Quick journal")
        Spacer(Modifier.height(8.dp))
        Text("How's this morning?", style = SecondBrainTypography.headlineMedium, color = Mist100)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Whatever's on your mind...", color = Mist400) },
            modifier = Modifier.fillMaxWidth().height(220.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Emerald400.copy(alpha = 0.6f),
                unfocusedBorderColor = Ink500,
                focusedTextColor = Mist100,
                unfocusedTextColor = Mist100,
                cursorColor = Emerald400
            )
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(error!!, style = SecondBrainTypography.bodySmall, color = Rose400)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (text.isBlank()) { onDone(); return@Button }
                saving = true
                error = null
                scope.launch {
                    val body = JSONObject()
                        .put("title", "Morning journal")
                        .put("content", text)
                        .put("para", "inbox")
                    val result = ApiClient.postJson("/api/notes", body)
                    saving = false
                    result.onSuccess { onDone() }
                    result.onFailure { error = it.message ?: "Couldn't save — try again" }
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = Ink950)
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Ink950, strokeWidth = 2.dp)
            } else {
                Text("Save", fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onSkip) {
            Text("Skip", color = Mist400)
        }
    }
}
