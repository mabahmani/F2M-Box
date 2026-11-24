package com.mab.f2mbox.ui.theme

import androidx.compose.material3.Typography as M3Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Typography
import com.mab.f2mbox.R

/**
 * Vazirmatn Round-Dots — clean, rounded letterforms that read well at
 * 10-foot TV distance, with full Persian/Arabic coverage.
 */
val VazirmatnRD = FontFamily(
    Font(R.font.vazirmatn_rd_thin, FontWeight.Thin),
    Font(R.font.vazirmatn_rd_extralight, FontWeight.ExtraLight),
    Font(R.font.vazirmatn_rd_light, FontWeight.Light),
    Font(R.font.vazirmatn_rd_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_rd_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_rd_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_rd_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_rd_extrabold, FontWeight.ExtraBold),
    Font(R.font.vazirmatn_rd_black, FontWeight.Black)
)

/**
 * Clean, generous type scale for the 10-foot TV experience, set in
 * Vazirmatn Round-Dots.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 62.sp
    ),
    displayMedium = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 46.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 42.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.SemiBold,
        fontSize = 29.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleMedium = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 29.sp,
        letterSpacing = 0.25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = VazirmatnRD,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.4.sp
    )
)

/** Same Vazirmatn scale for shared CMP composables (`androidx.compose.material3`). */
@OptIn(ExperimentalTvMaterial3Api::class)
fun Typography.toMaterial3Typography(): M3Typography = M3Typography(
    displayLarge = displayLarge,
    displayMedium = displayMedium,
    displaySmall = displaySmall,
    headlineLarge = headlineLarge,
    headlineMedium = headlineMedium,
    headlineSmall = headlineSmall,
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    titleSmall = titleSmall,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    bodySmall = bodySmall,
    labelLarge = labelLarge,
    labelMedium = labelMedium,
    labelSmall = labelSmall,
)
