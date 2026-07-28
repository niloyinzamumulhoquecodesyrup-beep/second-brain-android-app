package com.secondbrain.lock.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import com.secondbrain.lock.data.InstalledAppInfo
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbLabel
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.fullAuraBackground
import com.secondbrain.lock.util.drawableToImageBitmap

@Composable
fun AddAppScreen(
    apps: List<InstalledAppInfo>,
    onBack: () -> Unit,
    onConfirm: (InstalledAppInfo, Int, Int?) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var minutes by remember { mutableStateOf(30) }
    var openLimitEnabled by remember { mutableStateOf(false) }
    var openLimit by remember { mutableStateOf(10) }

    val filtered = remember(query, apps) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fullAuraBackground().padding(horizontal = 20.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Mist300)
            }
            Spacer(Modifier.width(4.dp))
            Text("Add a limit", style = SecondBrainTypography.headlineMedium, color = Color.White)
        }
        Spacer(Modifier.height(16.dp))

        if (selected == null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search installed apps", color = Mist400) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald400.copy(alpha = 0.6f),
                    unfocusedBorderColor = Ink500,
                    focusedTextColor = Mist100,
                    unfocusedTextColor = Mist100,
                    cursorColor = Emerald400
                )
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = app; minutes = 30 }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(app)
                        Spacer(Modifier.width(14.dp))
                        Text(app.label, style = SecondBrainTypography.bodyLarge, color = Mist100)
                    }
                }
            }
        } else {
            val app = selected!!
            SbCard(topBorderColor = Emerald400) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app)
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, style = SecondBrainTypography.titleMedium, color = Color.White)
                }
                Spacer(Modifier.height(24.dp))
                SbLabel("Daily limit")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { if (minutes > 5) minutes -= 5 }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Emerald400)
                    }
                    Text(
                        "$minutes min",
                        style = SecondBrainTypography.displayLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    IconButton(onClick = { if (minutes < 480) minutes += 5 }) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Emerald400)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().clickable { openLimitEnabled = !openLimitEnabled }
                ) {
                    SbLabel("Also cap opens/day")
                    Switch(
                        checked = openLimitEnabled,
                        onCheckedChange = { openLimitEnabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Emerald400, checkedThumbColor = Ink950)
                    )
                }
                if (openLimitEnabled) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { if (openLimit > 1) openLimit -= 1 }) {
                            Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Emerald400)
                        }
                        Text(
                            "$openLimit opens",
                            style = SecondBrainTypography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        IconButton(onClick = { if (openLimit < 100) openLimit += 1 }) {
                            Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Emerald400)
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { selected = null },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink600, contentColor = Mist300)
                    ) { Text("Back") }
                    Button(
                        onClick = { onConfirm(app, minutes, if (openLimitEnabled) openLimit else null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = Ink950)
                    ) { Text("Lock it in") }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: InstalledAppInfo) {
    val bitmap = remember(app.packageName) { drawableToImageBitmap(app.icon) }
    Image(
        painter = BitmapPainter(bitmap),
        contentDescription = null,
        modifier = Modifier.size(38.dp).clip(CircleShape)
    )
}
