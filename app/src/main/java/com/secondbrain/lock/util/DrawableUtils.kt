package com.secondbrain.lock.util

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
    val size = 96
    return drawable.toBitmap(width = size, height = size).asImageBitmap()
}
