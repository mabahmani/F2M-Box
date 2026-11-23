package com.mab.f2mbox.desktop.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mab.f2mbox.desktop.res.*
import org.jetbrains.compose.resources.Font

/**
 * Vazirmatn Round-Dots — clean, rounded letterforms that read well at a
 * comfortable couch distance, with full Persian/Arabic coverage. Bundled
 * with the app because system fonts cannot be relied on across platforms.
 *
 * Resource font loading is @Composable, so the family is built inside
 * composition (Task 2) rather than as a top-level val like the TV build.
 */
@Composable
fun vazirmatnFamily(): FontFamily = FontFamily(
    Font(Res.font.vazirmatn_rd_thin, FontWeight.Thin),
    Font(Res.font.vazirmatn_rd_extralight, FontWeight.ExtraLight),
    Font(Res.font.vazirmatn_rd_light, FontWeight.Light),
    Font(Res.font.vazirmatn_rd_regular, FontWeight.Normal),
    Font(Res.font.vazirmatn_rd_medium, FontWeight.Medium),
    Font(Res.font.vazirmatn_rd_semibold, FontWeight.SemiBold),
    Font(Res.font.vazirmatn_rd_bold, FontWeight.Bold),
    Font(Res.font.vazirmatn_rd_extrabold, FontWeight.ExtraBold),
    Font(Res.font.vazirmatn_rd_black, FontWeight.Black)
)

/**
 * The same generous type scale as the TV build, set in Vazirmatn
 * Round-Dots. A desktop window is viewed at ~1 m rather than 3 m, but
 * keeping the scale identical keeps the two UIs visually related.
 */
@Composable
fun f2mBoxTypography(): Typography = Typography(
    displayLarge = styled(vazirmatnFamily(), FontWeight.Bold, 52, 62),
    displayMedium = styled(vazirmatnFamily(), FontWeight.Bold, 44, 52),
    displaySmall = styled(vazirmatnFamily(), FontWeight.SemiBold, 36, 46),
    headlineLarge = styled(vazirmatnFamily(), FontWeight.SemiBold, 34, 42),
    headlineMedium = styled(vazirmatnFamily(), FontWeight.SemiBold, 29, 36),
    headlineSmall = styled(vazirmatnFamily(), FontWeight.Medium, 24, 32),
    titleLarge = styled(vazirmatnFamily(), FontWeight.SemiBold, 24, 32),
    titleMedium = styled(vazirmatnFamily(), FontWeight.Medium, 19, 26, 0.15f),
    titleSmall = styled(vazirmatnFamily(), FontWeight.Medium, 17, 24, 0.1f),
    bodyLarge = styled(vazirmatnFamily(), FontWeight.Normal, 19, 29, 0.25f),
    bodyMedium = styled(vazirmatnFamily(), FontWeight.Normal, 17, 24, 0.25f),
    bodySmall = styled(vazirmatnFamily(), FontWeight.Normal, 14, 19, 0.2f),
    labelLarge = styled(vazirmatnFamily(), FontWeight.Medium, 17, 24, 0.1f),
    labelMedium = styled(vazirmatnFamily(), FontWeight.Medium, 14, 19, 0.4f),
    labelSmall = styled(vazirmatnFamily(), FontWeight.Medium, 12, 17, 0.4f)
)

private fun styled(
    family: FontFamily,
    weight: FontWeight,
    fontSize: Int,
    lineHeight: Int,
    letterSpacing: Float = 0f
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)
