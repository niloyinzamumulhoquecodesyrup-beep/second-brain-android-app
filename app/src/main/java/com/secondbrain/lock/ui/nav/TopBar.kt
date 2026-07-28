package com.secondbrain.lock.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.secondbrain.lock.data.SecurePrefs
import com.secondbrain.lock.data.repo.ProfileRepository
import com.secondbrain.lock.data.repo.RemindersRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.network.dto.Reminder
import com.secondbrain.lock.ui.theme.Emerald400
import com.secondbrain.lock.ui.theme.GradientText
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink700
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbThemeState
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Slim top bar: wordmark + NotificationBell + ThemeToggle + user menu (Settings/Logout).
 *
 * [modifier] is expected to carry the actual glass look (haze blur + tint) from the caller,
 * since that requires a shared HazeState the caller owns — this composable only lays out its
 * own content, it doesn't paint an opaque background of its own.
 */
@Composable
fun TopBar(onOpenSettings: () -> Unit, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        launch { ProfileRepository.refresh() }
        while (isActive) {
            RemindersRepository.refresh()
            delay(60_000)
        }
    }
    val reminders by RemindersRepository.reminders.collectAsState()
    val dueReminders = reminders.filter { it.due }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GradientText("Slay Task", fontSize = 20.sp)
        Spacer(Modifier.weight(1f))
        NotificationBell(dueReminders)
        ThemeToggle()
        UserMenu(onOpenSettings = onOpenSettings, onLogout = onLogout)
    }
}

@Composable
private fun RowScope.NotificationBell(dueReminders: List<Reminder>) {
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                if (dueReminders.isNotEmpty()) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                contentDescription = if (dueReminders.isNotEmpty()) "${dueReminders.size} reminders waiting" else "Reminders",
                tint = Mist300
            )
        }
        // Built by hand rather than Badge/BadgedBox: the default M3 badge sits right at the
        // IconButton's touch-target edge and gets visually clipped there ("half eaten" circle).
        // A sibling Box anchored with real padding renders a clean, uncut circle instead.
        if (dueReminders.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(CircleShape)
                    .background(Rose400)
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    dueReminders.size.toString(),
                    color = Ink950,
                    fontSize = 10.sp,
                    style = SecondBrainTypography.labelSmall
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (dueReminders.isEmpty()) {
            DropdownMenuItem(text = { Text("Nothing due right now") }, onClick = {}, enabled = false)
        } else {
            dueReminders.forEach { reminder ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(reminder.message, color = Mist100, style = SecondBrainTypography.bodyMedium)
                            Spacer(Modifier.width(4.dp))
                            Row {
                                if (reminder.kind == "routine_suggestion") {
                                    TextButton(onClick = {
                                        scope.launch { RemindersRepository.act(reminder.id, "accept") }
                                    }) { Text("Accept", color = Emerald400) }
                                    TextButton(onClick = {
                                        scope.launch { RemindersRepository.act(reminder.id, "dismiss") }
                                    }) { Text("Not now", color = Mist300) }
                                } else {
                                    TextButton(onClick = {
                                        scope.launch { RemindersRepository.act(reminder.id, "done") }
                                    }) { Text("Done", color = Emerald400) }
                                    TextButton(onClick = {
                                        scope.launch { RemindersRepository.act(reminder.id, "snooze", 10) }
                                    }) { Text("Snooze 10m", color = Mist300) }
                                }
                            }
                        }
                    },
                    onClick = {}
                )
            }
        }
        }
    }
}

@Composable
private fun ThemeToggle() {
    val context = LocalContext.current
    val isLight = SbThemeState.mode == ThemeMode.LIGHT
    IconButton(onClick = {
        val next = if (isLight) ThemeMode.DARK else ThemeMode.LIGHT
        SecurePrefs.setTheme(context, next.storageKey)
        SbThemeState.mode = next
    }) {
        Icon(
            if (isLight) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = "Toggle theme",
            tint = Mist300
        )
    }
}

@Composable
private fun UserMenu(onOpenSettings: () -> Unit, onLogout: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val profile by ProfileRepository.profile.collectAsState()

    IconButton(onClick = { expanded = true }) {
        val p = profile
        if (p?.hasAvatar == true) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(1.dp, Ink500), CircleShape)
            ) {
                AsyncImage(
                    model = "${ApiClient.baseUrl}/api/auth/avatar",
                    contentDescription = "Account",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        } else if (p != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Ink700)
                    .border(BorderStroke(1.dp, Ink500), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initials(p.name, p.email), color = Mist100, fontSize = 12.sp, style = SecondBrainTypography.labelSmall)
            }
        } else {
            Icon(Icons.Filled.AccountCircle, contentDescription = "Account", tint = Mist300)
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.widthIn(min = 160.dp)) {
        DropdownMenuItem(text = { Text("Settings") }, onClick = { expanded = false; onOpenSettings() })
        DropdownMenuItem(text = { Text("Log out", color = Rose400) }, onClick = { expanded = false; onLogout() })
    }
}

/** Mirrors the web app's UserMenu.js getInitials(): first letters of up to two name/email words. */
private fun initials(name: String?, email: String): String {
    val source = name?.trim()?.takeUnless { it.isBlank() } ?: email
    val parts = source.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        parts.size == 1 && parts[0].length >= 2 -> parts[0].take(2).uppercase()
        parts.isNotEmpty() -> parts[0].take(1).uppercase()
        else -> "?"
    }
}
