package com.secondbrain.lock.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.fullAuraBackground

data class PermissionStep(
    val title: String,
    val description: String,
    val granted: Boolean,
    val onRequest: () -> Unit
)

@Composable
fun OnboardingScreen(
    steps: List<PermissionStep>,
    allGranted: Boolean,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fullAuraBackground()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp)
    ) {
        SbLabel("Setup")
        Spacer(Modifier.height(8.dp))
        Text(
            "Second Brain Lock",
            style = SecondBrainTypography.displayLarge,
            color = Color.White
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Three permissions let this app see when a limited app opens and hard-lock it the moment your daily time is up. Nothing is sent off-device — there's no account and no network access.",
            style = SecondBrainTypography.bodyMedium,
            color = Mist400
        )

        Spacer(Modifier.height(32.dp))

        steps.forEach { step ->
            SbCard(
                modifier = Modifier.padding(bottom = 14.dp),
                topBorderColor = if (step.granted) Emerald400 else null
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (step.granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (step.granted) Emerald400 else Mist400,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(step.title, style = SecondBrainTypography.titleMedium, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text(step.description, style = SecondBrainTypography.bodySmall, color = Mist300)
                    }
                }
                if (!step.granted) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = step.onRequest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald400.copy(alpha = 0.12f),
                            contentColor = Emerald400
                        )
                    ) { Text("Grant permission") }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onContinue,
            enabled = allGranted,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Emerald400,
                contentColor = Ink950,
                disabledContainerColor = Mist400.copy(alpha = 0.15f)
            )
        ) {
            Text(if (allGranted) "Continue" else "Grant all permissions to continue", fontWeight = FontWeight.Medium)
        }
    }
}
