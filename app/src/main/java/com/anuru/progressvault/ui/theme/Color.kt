package com.anuru.progressvault.ui.theme

import androidx.compose.ui.graphics.Color

/** Dark tech palette. The app is always dark. */
object VaultColors {
    val Background = Color(0xFF0B0F14)
    val Surface = Color(0xFF121821)
    val SurfaceRaised = Color(0xFF17202B)
    val Border = Color(0xFF1F2A37)
    val BorderStrong = Color(0xFF2B3A4C)

    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF9AA7B8)
    val TextMuted = Color(0xFF5E6B7D)

    val Cyan = Color(0xFF22D3EE)
    val Green = Color(0xFF4ADE80)
    val Purple = Color(0xFFA78BFA)
    val Amber = Color(0xFFFBBF24)
    val Pink = Color(0xFFF472B6)
    val Blue = Color(0xFF60A5FA)

    val Error = Color(0xFFF87171)
    val OnAccent = Color(0xFF051018)
}

/** Converts a stored ARGB long (e.g. 0xFF22D3EE) to a Compose colour. */
fun accentColor(argb: Long): Color = Color(argb.toInt())
