package com.secondbrain.lock.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.secondbrain.lock.data.SecurePrefs
import com.secondbrain.lock.data.repo.ProfileRepository
import com.secondbrain.lock.data.repo.RemindersRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink700
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
 * Slim top bar: logo mark + wordmark, then ThemeToggle + user menu (Settings/Logout).
 *
 * Scrolls away with the page like any other content — callers place this as the first item in
 * their own scrollable column rather than pinning it via Scaffold's `topBar` slot, so it carries
 * no background/blur of its own.
 */
@Composable
fun TopBar(onOpenSettings: () -> Unit, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        launch { ProfileRepository.refresh() }
        while (isActive) {
            // Kept alive here (rather than in a removed notification-bell UI) since NudgesStrip
            // on the Work tab reads RemindersRepository.reminders without polling it itself.
            RemindersRepository.refresh()
            delay(60_000)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // No horizontal padding here — callers already place this inside a Column that
            // applies 16dp of horizontal padding to everything, cards included. Adding it again
            // here would double-inset the logo/icons past where the cards below actually line up.
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Slay Task", color = Mist100, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        ThemeToggle()
        Spacer(Modifier.width(16.dp))
        UserMenu(onOpenSettings = onOpenSettings, onLogout = onLogout)
    }
}

@Composable
private fun ThemeToggle() {
    val context = LocalContext.current
    val isLight = SbThemeState.mode == ThemeMode.LIGHT
    IconButton(
        onClick = {
            val next = if (isLight) ThemeMode.DARK else ThemeMode.LIGHT
            SecurePrefs.setTheme(context, next.storageKey)
            SbThemeState.mode = next
        },
        modifier = Modifier.size(32.dp)
    ) {
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
    val avatarVersion by ProfileRepository.avatarVersion.collectAsState()

    // DropdownMenu anchors to whatever layout node directly contains it — without this Box
    // wrapping both the trigger and the menu, that node was UserMenu's caller (TopBar's Row),
    // so the menu popped up at the Row's own origin (the far left of the screen) instead of
    // under the avatar button.
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
            val p = profile
            if (p?.hasAvatar == true) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Ink700)
                        .border(BorderStroke(1.dp, Ink500), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Initials stay as a backdrop underneath — AsyncImage renders nothing while
                    // loading or on error, so without this the circle goes empty every time the
                    // `?v=` cache-buster changes the URL (i.e. right after every upload).
                    Text(initials(p.name, p.email), color = Mist100, fontSize = 12.sp, style = SecondBrainTypography.labelSmall)
                    AsyncImage(
                        // `?v=` busts Coil's cache after a new photo is uploaded — see
                        // ProfileRepository.avatarVersion for why the bare URL alone isn't enough.
                        model = "${ApiClient.baseUrl}/api/auth/avatar?v=$avatarVersion",
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
