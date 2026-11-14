package com.anuru.progressvault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.anuru.progressvault.R

/** Bundled JetBrains Mono (OFL) — used for numbers, stats, headings and labels. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Clean system sans for body copy. */
private val Body = FontFamily.SansSerif

val VaultTypography = Typography(
    displayLarge = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp),
    displaySmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
)
