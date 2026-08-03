package com.secondbrain.lock.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.secondbrain.lock.data.FeedbackUtil
import com.secondbrain.lock.data.SoundHapticsPrefs
import com.secondbrain.lock.data.repo.ProfileRepository
import com.secondbrain.lock.network.ApiClient
import com.secondbrain.lock.ui.theme.Ink500
import com.secondbrain.lock.ui.theme.Ink600
import com.secondbrain.lock.ui.theme.Ink700
import com.secondbrain.lock.ui.theme.Ink800
import com.secondbrain.lock.ui.theme.Ink900
import com.secondbrain.lock.ui.theme.Mist100
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.ui.theme.Mist400
import com.secondbrain.lock.ui.theme.Rose400
import com.secondbrain.lock.ui.theme.SbCard
import com.secondbrain.lock.ui.theme.SbSectionTitle
import com.secondbrain.lock.ui.theme.SecondBrainTypography
import com.secondbrain.lock.ui.theme.StreakAccent
import com.secondbrain.lock.ui.theme.fullAuraBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Account-level settings reached from the top bar's avatar menu — distinct from the Shield tab's
 * gear icon, which stays scoped to app-blocking config ([SettingsScreen]). Edit profile
 * (name/avatar), change password, notification quiet hours, and deactivate account, all against
 * the `/api/auth` endpoints and `/api/notification-prefs`. Follows the Work-tab redesign's visual language
 * (StreakAccent section titles, the AllTasksScreen-style circular back button) rather than the
 * older Emerald/SbLabel look, per the same "current design" pass the Shield tab got.
 */
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    onDeactivated: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val profile by ProfileRepository.profile.collectAsState()

    Box(Modifier.fullAuraBackground().fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp
                )
        ) {
            AccountSettingsHeader(onBack)
            Spacer(Modifier.height(20.dp))

            EditProfileSection(name = profile?.name, email = profile?.email.orEmpty(), hasAvatar = profile?.hasAvatar == true)
            Spacer(Modifier.height(24.dp))

            ChangePasswordSection()
            Spacer(Modifier.height(24.dp))

            NotificationsSection()
            Spacer(Modifier.height(24.dp))

            HapticsAndSoundsSection()
            Spacer(Modifier.height(24.dp))

            DeactivateSection(onDeactivated = onDeactivated)
        }
    }
}

@Composable
private fun AccountSettingsHeader(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Ink900)
                .border(BorderStroke(1.dp, Ink600), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("←", color = Mist100, fontSize = 16.sp) }
        Spacer(Modifier.width(14.dp))
        Text("Account settings", color = Mist100, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun EditProfileSection(name: String?, email: String, hasAvatar: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val avatarVersion by ProfileRepository.avatarVersion.collectAsState()
    var displayName by remember(name) { mutableStateOf(name.orEmpty()) }
    var avatarBusy by remember { mutableStateOf(false) }
    var nameBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }

    // Picking only stages the photo for cropping — nothing is uploaded until the crop dialog's
    // "Use photo" is tapped.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropUri = uri
    }

    SbSectionTitle("Edit profile", color = StreakAccent)
    Spacer(Modifier.height(10.dp))
    SbCard {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Ink700)
                .border(BorderStroke(1.dp, Ink500), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Initials stay as a backdrop underneath — AsyncImage renders nothing while loading
            // or on error, so without this the circle goes empty every time the `?v=`
            // cache-buster changes the URL (i.e. right after every upload).
            Text(initials(displayName, email), color = Mist100, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (hasAvatar) {
                AsyncImage(
                    // `?v=` busts Coil's cache after a new photo is uploaded — see
                    // ProfileRepository.avatarVersion for why the bare URL alone isn't enough.
                    model = ImageRequest.Builder(context).data("${ApiClient.baseUrl}/api/auth/avatar?v=$avatarVersion").build(),
                    contentDescription = "Profile photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !avatarBusy,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Mist100),
            border = BorderStroke(1.dp, Ink500)
        ) { Text(if (avatarBusy) "Uploading…" else "Change photo") }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name") },
                singleLine = true,
                enabled = !nameBusy,
                modifier = Modifier.weight(1f),
                colors = accountFieldColors()
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = {
                    nameBusy = true
                    error = null
                    scope.launch {
                        ApiClient.updateProfileName(displayName.trim())
                            .onSuccess { ProfileRepository.refresh() }
                            .onFailure { error = it.message ?: "Couldn't save name" }
                        nameBusy = false
                    }
                },
                enabled = !nameBusy && displayName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White)
            ) { Text("Save") }
        }
        Spacer(Modifier.height(10.dp))
        Text("Account email: $email", style = SecondBrainTypography.bodySmall, color = Mist400)

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = SecondBrainTypography.bodySmall, color = Rose400)
        }
    }

    cropUri?.let { uri ->
        AvatarCropDialog(
            uri = uri,
            onDismiss = { cropUri = null },
            onConfirmed = { base64 ->
                cropUri = null
                avatarBusy = true
                error = null
                scope.launch {
                    ApiClient.uploadAvatar("image/jpeg", base64)
                        .onSuccess { ProfileRepository.refresh(); ProfileRepository.bumpAvatarVersion() }
                        .onFailure { error = it.message ?: "Couldn't upload photo" }
                    avatarBusy = false
                }
            }
        )
    }
}

/**
 * A minimal pinch-to-zoom/drag-to-reposition 1:1 crop editor — square viewport, "cover"-fit at
 * min zoom so the frame is always fully covered (no empty edges), pan/zoom clamped so the crop
 * region never leaves the source image. [onConfirmed] receives an unprefixed base64 JPEG under
 * 200KB, downscaled to a max of 512px, ready to hand straight to [ApiClient.uploadAvatar].
 */
@Composable
private fun AvatarCropDialog(uri: Uri, onDismiss: () -> Unit, onConfirmed: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(uri) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    LaunchedEffect(uri) {
        val decoded = withContext(Dispatchers.IO) {
            runCatching { decodeSampledBitmap(context, uri, maxDim = 1600) }.getOrNull()
        }
        if (decoded == null) loadFailed = true else bitmap = decoded
    }

    val viewportDp = 280.dp
    val viewportPx = with(density) { viewportDp.toPx() }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Ink900)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Crop photo", color = Mist100, style = SecondBrainTypography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Drag to reposition, pinch to zoom", color = Mist400, style = SecondBrainTypography.bodySmall)
            Spacer(Modifier.height(16.dp))

            val bmp = bitmap
            if (bmp == null) {
                Box(Modifier.size(viewportDp), contentAlignment = Alignment.Center) {
                    Text(
                        if (loadFailed) "Couldn't load that photo" else "Loading…",
                        color = if (loadFailed) Rose400 else Mist400,
                        style = SecondBrainTypography.bodySmall
                    )
                }
            } else {
                val fitScale = viewportPx / minOf(bmp.width, bmp.height).toFloat()
                Box(
                    modifier = Modifier
                        .size(viewportDp)
                        .clip(CircleShape)
                        .background(Ink800)
                        .pointerInput(bmp) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                val halfW = bmp.width * fitScale * scale / 2f
                                val halfH = bmp.height * fitScale * scale / 2f
                                val maxPanX = (halfW - viewportPx / 2f).coerceAtLeast(0f)
                                val maxPanY = (halfH - viewportPx / 2f).coerceAtLeast(0f)
                                panX = (panX + pan.x).coerceIn(-maxPanX, maxPanX)
                                panY = (panY + pan.y).coerceIn(-maxPanY, maxPanY)
                            }
                        }
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(
                                with(density) { (bmp.width * fitScale).toDp() },
                                with(density) { (bmp.height * fitScale).toDp() }
                            )
                            .align(Alignment.Center)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = panX
                                translationY = panY
                            }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", color = Mist400) }
                Button(
                    onClick = {
                        val source = bitmap ?: return@Button
                        busy = true
                        val fitScale = viewportPx / minOf(source.width, source.height).toFloat()
                        scope.launch {
                            val base64 = withContext(Dispatchers.Default) {
                                cropAndEncodeAvatar(source, fitScale, scale, panX, panY, viewportPx)
                            }
                            busy = false
                            onConfirmed(base64)
                        }
                    },
                    enabled = !busy && bitmap != null,
                    colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White)
                ) { Text(if (busy) "Processing…" else "Use photo") }
            }
        }
    }
}

/** Decodes [uri] downsampled so its longer side is roughly [maxDim] px — avoids holding a full
 * multi-megapixel camera photo in memory just to crop and shrink it down to an avatar. */
private fun decodeSampledBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // decodeStream always returns null when inJustDecodeBounds is set — it only fills in
    // bounds.outWidth/outHeight, never a Bitmap — so success/failure has to be read from those
    // fields, not from this call's return value.
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= maxDim || bounds.outHeight / (sampleSize * 2) >= maxDim) {
        sampleSize *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

/** Maps the crop viewport's visible square back onto [source]'s own pixel space (inverse of the
 * [AvatarCropDialog] preview's fit-scale/zoom/pan transform), extracts that square, downscales to
 * a max 512px avatar, and JPEG-compresses it under 200KB before base64-encoding. */
private fun cropAndEncodeAvatar(source: Bitmap, fitScale: Float, scale: Float, panX: Float, panY: Float, viewportPx: Float): String {
    val totalScale = fitScale * scale
    val imageCenterX = viewportPx / 2f + panX
    val imageCenterY = viewportPx / 2f + panY
    val cropSize = (viewportPx / totalScale).coerceAtMost(minOf(source.width, source.height).toFloat())
    val cropX = (source.width / 2f - imageCenterX / totalScale).coerceIn(0f, source.width - cropSize)
    val cropY = (source.height / 2f - imageCenterY / totalScale).coerceIn(0f, source.height - cropSize)
    val cropped = Bitmap.createBitmap(source, cropX.roundToInt(), cropY.roundToInt(), cropSize.roundToInt(), cropSize.roundToInt())

    val maxDim = 512
    val outScale = minOf(1f, maxDim.toFloat() / cropped.width)
    val resized = if (outScale < 1f) {
        Bitmap.createScaledBitmap(cropped, (cropped.width * outScale).toInt(), (cropped.height * outScale).toInt(), true)
    } else cropped

    return Base64.encodeToString(compressJpegUnder200KB(resized), Base64.NO_WRAP)
}

/** Steps JPEG quality down until the encoded size is under 200KB (the frontend-side size cap for
 * avatar uploads), floored at quality 30 so it never degrades to mush trying to hit the target. */
private fun compressJpegUnder200KB(bitmap: Bitmap): ByteArray {
    val maxBytes = 200_000
    var quality = 90
    var out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    while (out.size() > maxBytes && quality > 30) {
        quality -= 10
        out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    }
    return out.toByteArray()
}

@Composable
private fun ChangePasswordSection() {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    SbSectionTitle("Change password", color = StreakAccent)
    Spacer(Modifier.height(10.dp))
    SbCard {
        OutlinedTextField(
            value = current,
            onValueChange = { current = it; success = false },
            label = { Text("Current password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = accountFieldColors()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = new,
            onValueChange = { new = it; success = false },
            label = { Text("New password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = accountFieldColors()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it; success = false },
            label = { Text("Confirm new password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = accountFieldColors()
        )

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = SecondBrainTypography.bodySmall, color = Rose400)
        }
        if (success) {
            Spacer(Modifier.height(10.dp))
            Text("Password updated.", style = SecondBrainTypography.bodySmall, color = StreakAccent)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                error = when {
                    current.isBlank() || new.isBlank() -> "Fill in both password fields"
                    new != confirm -> "New password and confirmation don't match"
                    new.length < 8 -> "New password must be at least 8 characters"
                    else -> null
                }
                if (error != null) return@Button
                busy = true
                scope.launch {
                    ApiClient.changePassword(current, new)
                        .onSuccess { current = ""; new = ""; confirm = ""; success = true }
                        .onFailure { error = it.message ?: "Couldn't update password" }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White)
        ) { Text(if (busy) "Updating…" else "Update password") }
    }
}

@Composable
private fun NotificationsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var quietStartMin by remember { mutableStateOf<Int?>(null) }
    var quietEndMin by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ApiClient.getNotificationPrefs().onSuccess {
            quietStartMin = it.quietStartMin
            quietEndMin = it.quietEndMin
        }
    }

    fun pickTime(initial: Int?, onPicked: (Int) -> Unit) {
        val base = initial ?: (22 * 60)
        TimePickerDialog(context, { _, hourOfDay, minute -> onPicked(hourOfDay * 60 + minute) }, base / 60, base % 60, false).show()
    }

    SbSectionTitle("Notifications", color = StreakAccent)
    Spacer(Modifier.height(10.dp))
    SbCard {
        Text(
            "Quiet hours — no nudges fire during this window; they just wait until it ends.",
            style = SecondBrainTypography.bodySmall,
            color = Mist400
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("From", style = SecondBrainTypography.bodySmall, color = Mist300)
                Spacer(Modifier.height(6.dp))
                QuietHourChip(quietStartMin) { pickTime(quietStartMin) { quietStartMin = it } }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("To", style = SecondBrainTypography.bodySmall, color = Mist300)
                Spacer(Modifier.height(6.dp))
                QuietHourChip(quietEndMin) { pickTime(quietEndMin) { quietEndMin = it } }
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        ApiClient.updateNotificationPrefs(quietStartMin, quietEndMin)
                            .onFailure { error = it.message ?: "Couldn't save" }
                        busy = false
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = StreakAccent, contentColor = Color.White)
            ) { Text("Save") }
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = SecondBrainTypography.bodySmall, color = Rose400)
        }
    }
}

@Composable
private fun QuietHourChip(minuteOfDay: Int?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Ink800)
            .border(BorderStroke(1.dp, Ink500), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            minuteOfDay?.let(::formatMinuteOfDay12h) ?: "Not set",
            style = SecondBrainTypography.bodyMedium,
            color = if (minuteOfDay != null) Mist100 else Mist400
        )
    }
}

@Composable
private fun HapticsAndSoundsSection() {
    val context = LocalContext.current
    var hapticsEnabled by remember { mutableStateOf(SoundHapticsPrefs.isHapticsEnabled(context)) }
    var soundsEnabled by remember { mutableStateOf(SoundHapticsPrefs.isSoundsEnabled(context)) }

    SbSectionTitle("Haptics & sounds", color = StreakAccent)
    Spacer(Modifier.height(10.dp))
    SbCard {
        SettingsToggleRow(
            title = "Haptic feedback",
            description = "Vibrate for gestures like spinning the PARA card stack.",
            checked = hapticsEnabled,
            onCheckedChange = {
                hapticsEnabled = it
                SoundHapticsPrefs.setHapticsEnabled(context, it)
                if (it) FeedbackUtil.spinTick(context)
            }
        )
        Spacer(Modifier.height(16.dp))
        SettingsToggleRow(
            title = "In-app sounds",
            description = "Play short tick sounds for gestures like spinning the PARA card stack.",
            checked = soundsEnabled,
            onCheckedChange = {
                soundsEnabled = it
                SoundHapticsPrefs.setSoundsEnabled(context, it)
                if (it) FeedbackUtil.spinTick(context)
            }
        )
    }
}

@Composable
private fun SettingsToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SecondBrainTypography.titleMedium, color = Mist100)
            Spacer(Modifier.height(4.dp))
            Text(description, style = SecondBrainTypography.bodySmall, color = Mist300)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = StreakAccent, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun DeactivateSection(onDeactivated: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    SbSectionTitle("Deactivate account", color = Rose400)
    Spacer(Modifier.height(10.dp))
    SbCard(topBorderColor = Rose400) {
        Text(
            "This signs you out and blocks login. Your data is kept, but you won't be able to access it. Contact support to reactivate.",
            style = SecondBrainTypography.bodySmall,
            color = Mist300
        )
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = { showDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose400),
            border = BorderStroke(1.dp, Rose400)
        ) { Text("Deactivate my account") }
    }

    if (showDialog) {
        DeactivateDialog(onDismiss = { showDialog = false }, onDeactivated = onDeactivated)
    }
}

@Composable
private fun DeactivateDialog(onDismiss: () -> Unit, onDeactivated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Ink900,
        title = { Text("Deactivate account?", color = Mist100) },
        text = {
            Column {
                Text(
                    "Enter your password to confirm. You'll be signed out immediately.",
                    style = SecondBrainTypography.bodySmall,
                    color = Mist300
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = accountFieldColors()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = SecondBrainTypography.bodySmall, color = Rose400)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (password.isBlank()) { error = "Enter your password"; return@TextButton }
                    busy = true
                    scope.launch {
                        ApiClient.deactivateAccount(password)
                            .onSuccess { onDeactivated() }
                            .onFailure { error = it.message ?: "Couldn't deactivate account"; busy = false }
                    }
                },
                enabled = !busy
            ) { Text(if (busy) "Deactivating…" else "Deactivate", color = Rose400) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", color = Mist400) }
        }
    )
}

@Composable
private fun accountFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = StreakAccent.copy(alpha = 0.6f),
    unfocusedBorderColor = Ink500,
    focusedTextColor = Mist100,
    unfocusedTextColor = Mist100,
    focusedLabelColor = StreakAccent,
    unfocusedLabelColor = Mist400,
    cursorColor = StreakAccent
)

private fun formatMinuteOfDay12h(minuteOfDay: Int): String {
    val hour24 = minuteOfDay / 60
    val minute = minuteOfDay % 60
    val amPm = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return String.format("%d:%02d %s", hour12, minute, amPm)
}

/** Mirrors TopBar.kt's UserMenu getInitials(): first letters of up to two name/email words. */
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
