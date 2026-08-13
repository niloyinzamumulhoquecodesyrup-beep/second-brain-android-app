package com.secondbrain.lock.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.fullAuraBackground

/** Shared by [LoginScreen] and [RegisterScreen]. Mirrors [AccountSettingsScreen]'s field styling
 * — the same "current design" pass (StreakAccent + SbCard/SbSectionTitle instead of the old
 * Emerald/SbLabel look). */
@Composable
internal fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = StreakAccent.copy(alpha = 0.6f),
    unfocusedBorderColor = Ink500,
    focusedTextColor = Mist100,
    unfocusedTextColor = Mist100,
    focusedLabelColor = StreakAccent,
    unfocusedLabelColor = Mist400,
    cursorColor = StreakAccent
)

/** Shared by [LoginScreen] and [RegisterScreen]. */
@Composable
internal fun AuthHeader(eyebrow: String, title: String) {
    Text("Slay Task", color = Mist100, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
    Spacer(Modifier.height(20.dp))
    SbSectionTitle(eyebrow, color = Mist300)
    Spacer(Modifier.height(8.dp))
    Text(title, color = Mist100, fontSize = 28.sp, fontWeight = FontWeight.Light)
}

@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (email: String, password: String) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(Modifier.fullAuraBackground().fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 64.dp)
        ) {
            AuthHeader(eyebrow = "Welcome back", title = "Sign in")
            Spacer(Modifier.height(8.dp))
            Text(
                "Use the same account you use on the web.",
                style = SecondBrainTypography.bodyMedium,
                color = Mist400
            )

            Spacer(Modifier.height(24.dp))

            SbCard {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(errorMessage, style = SecondBrainTypography.bodySmall, color = Rose400)
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { onLogin(email.trim(), password) },
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StreakAccent,
                        contentColor = Color.White,
                        disabledContainerColor = Mist400.copy(alpha = 0.15f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Log in", fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row {
                Text("No account yet? ", style = SecondBrainTypography.bodySmall, color = Mist300)
                Text(
                    "Create one",
                    style = SecondBrainTypography.bodySmall,
                    color = StreakAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !isLoading, onClick = onNavigateToRegister)
                )
            }
        }
    }
}
