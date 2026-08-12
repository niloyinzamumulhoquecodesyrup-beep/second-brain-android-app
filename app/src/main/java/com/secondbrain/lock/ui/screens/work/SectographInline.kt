package com.secondbrain.lock.ui.screens.work

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.secondbrain.lock.data.repo.PlannerRepository
import com.secondbrain.lock.ui.theme.Ink950
import com.secondbrain.lock.ui.theme.Mist300
import com.secondbrain.lock.widget.SectographRenderer

/**
 * Promotes the home-screen widget's sectograph face into the app itself (P18) — same
 * [SectographRenderer.render], same Bitmap, no second drawing implementation. Cached in memory
 * keyed on a hash of the current routine set so it isn't re-rasterized on every recomposition;
 * re-renders only when that hash actually changes.
 */
@Composable
fun SectographInline(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val routines by PlannerRepository.routines.collectAsState()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cachedKey by remember { mutableStateOf<Int?>(null) }
    var expanded by remember { mutableStateOf(false) }

    val routineSetHash = routines.hashCode()
    LaunchedEffect(routineSetHash) {
        if (cachedKey != routineSetHash) {
            bitmap = SectographRenderer.render(context)
            cachedKey = routineSetHash
        }
    }

    bitmap?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Today's schedule — tap for full view",
            modifier = modifier
                .size(120.dp)
                .clip(CircleShape)
                .clickable { expanded = true }
        )
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Ink950), contentAlignment = Alignment.Center) {
                bitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Today's schedule",
                        modifier = Modifier
                            .size(320.dp)
                            .semantics { contentDescription = "Today's full schedule" }
                    )
                }
                IconButton(
                    onClick = { expanded = false },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Mist300)
                }
            }
        }
    }
}
