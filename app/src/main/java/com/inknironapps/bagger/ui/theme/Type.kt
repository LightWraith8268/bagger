package com.inknironapps.bagger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DisplayFamily = FontFamily.Serif      // EB Garamond shipped in later plan
private val UiFamily      = FontFamily.SansSerif  // Inter shipped in later plan

val BaggerTypography = Typography(
    displayLarge  = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 48.sp),
    titleLarge    = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Medium, fontSize = 24.sp),
    bodyLarge     = TextStyle(fontFamily = UiFamily,      fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium    = TextStyle(fontFamily = UiFamily,      fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge    = TextStyle(fontFamily = UiFamily,      fontWeight = FontWeight.Medium, fontSize = 14.sp)
)
