package com.secondbrain.lock.ui.screens.mindverse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.secondbrain.lock.data.repo.MindverseRepository
import com.secondbrain.lock.ui.screens.work.StreakSurface
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.fullAuraBackground
import kotlinx.coroutines.launch

/**
 * Ports pages/other-brains.js's top-level MINDVERSE shell: an identity gate (every write
 * endpoint 400s without a display name set first, matching the web client's own behavior),
 * then Mindcord underneath. The Other Brains sub-tab (community feed/live chat/suggestion
 * box/currently-studying) was dropped — not needed for now.
 */
@Composable
fun MindverseScreen(
    contentPadding: PaddingValues = PaddingValues(),
    topBar: @Composable () -> Unit = {},
    onOpenRoom: () -> Unit = {}
) {
    val identity by MindverseRepository.identity.collectAsState()
    val identityChecked by MindverseRepository.identityChecked.collectAsState()

    LaunchedEffect(Unit) { MindverseRepository.refreshIdentity() }

    Box(Modifier.fullAuraBackground().fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp
                )
        ) {
            topBar()
            Spacer(Modifier.height(16.dp))

            when {
                !identityChecked -> Text("Loading…", color = Mist400, style = SecondBrainTypography.bodyMedium)
                identity == null -> PickDisplayNameCard()
                else -> MindcordTab(onOpenRoom = onOpenRoom)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PickDisplayNameCard() {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    StreakSurface {
        SbLabel("Pick a handle", color = StreakAccent)
        Spacer(Modifier.height(8.dp))
        Text(
            "MINDVERSE is cross-account and anonymous — pick a display name that isn't your email " +
                "or real name. You'll get a random avatar automatically.",
            color = Mist300,
            style = SecondBrainTypography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 24) name = it },
            placeholder = { Text("e.g. quietfox42", color = Mist400) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = StreakAccent.copy(alpha = 0.6f),
                unfocusedBorderColor = Ink500,
                focusedTextColor = Mist100,
                unfocusedTextColor = Mist100,
                cursorColor = StreakAccent
            )
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = Rose400, style = SecondBrainTypography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                saving = true
                error = null
                scope.launch {
                    val result = MindverseRepository.setDisplayName(name.trim())
                    saving = false
                    result.onFailure { error = it.message ?: "Couldn't save" }
                }
            },
            enabled = !saving && name.trim().length >= 2,
            colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White)
        ) { Text("Save") }
    }
}
