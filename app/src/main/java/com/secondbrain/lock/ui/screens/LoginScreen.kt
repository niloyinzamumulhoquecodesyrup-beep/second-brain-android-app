package com.secondbrain.lock.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.GradientText
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.fullAuraBackground

@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (email: String, password: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Emerald400.copy(alpha = 0.6f),
        unfocusedBorderColor = Ink500,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = Emerald400
    )

    Column(
        modifier = Modifier
            .fullAuraBackground()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 64.dp)
    ) {
        SbLabel("Welcome back")
        Spacer(Modifier.height(8.dp))
        GradientText("Slay Task", fontSize = 36.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Sign in with the same account you use on the web.",
            style = SecondBrainTypography.bodyMedium,
            color = Mist400
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(errorMessage, style = SecondBrainTypography.bodySmall, color = Rose400)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onLogin(email.trim(), password) },
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Emerald400,
                contentColor = Ink950,
                disabledContainerColor = Mist400.copy(alpha = 0.15f)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Ink950, strokeWidth = 2.dp)
            } else {
                Text("Log in", fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "No account yet? Create one in the web app, then sign in here.",
            style = SecondBrainTypography.bodySmall,
            color = Mist300
        )
    }
}
