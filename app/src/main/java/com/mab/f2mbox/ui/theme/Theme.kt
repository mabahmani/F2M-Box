package com.mab.f2mbox.ui.theme

import androidx.compose.material3.MaterialTheme as M3MaterialTheme
import com.mab.f2mbox.ui.theme.f2mMaterial3DarkColorScheme
import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * F2M Box brand theme. TV is a dark, 10-foot experience, so the theme is
 * always dark: near-black backgrounds with cinematic purple/blue accents.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun F2MBoxTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = BrandPurple,
        onPrimary = TextBright,
        primaryContainer = BrandPurpleDeep,
        onPrimaryContainer = TextBright,
        secondary = BrandBlue,
        onSecondary = TextBright,
        secondaryContainer = BrandBlueDeep,
        onSecondaryContainer = TextBright,
        tertiary = BrandPurple,
        onTertiary = TextBright,
        background = BgNavy,
        onBackground = TextBright,
        surface = SurfaceNavy,
        onSurface = TextBright,
        surfaceVariant = SurfaceNavyVariant,
        onSurfaceVariant = TextMuted,
        inverseSurface = TextBright,
        inverseOnSurface = BgNavy,
        border = SurfaceNavyVariant,
        borderVariant = BorderNavy,
        scrim = BgNavy
    )
    val m3Colors = f2mMaterial3DarkColorScheme()
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
    ) {
        // Shared CMP composables from `:shared` read Material 3 locals.
        M3MaterialTheme(
            colorScheme = m3Colors,
            typography = Typography.toMaterial3Typography(),
        ) {
            content()
        }
    }
}
