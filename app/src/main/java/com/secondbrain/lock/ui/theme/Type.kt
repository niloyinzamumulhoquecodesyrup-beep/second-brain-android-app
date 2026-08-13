package com.secondbrain.lock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System serif approximates Cormorant Garamond's elegant, light display feel
// without a network font fetch — the lock screen must render fully offline.
val SerifDisplay = FontFamily.Serif
val SansBody = FontFamily.SansSerif

val SecondBrainTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Light,
        fontSize = 40.sp,
        letterSpacing = 0.2.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Light,
        fontSize = 30.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        // P24: wide tracking on 11sp text is elegant but measurably slower to read, and this
        // codebase uses labelSmall for nearly every section header.
        letterSpacing = 1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = SansBody,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )
)
