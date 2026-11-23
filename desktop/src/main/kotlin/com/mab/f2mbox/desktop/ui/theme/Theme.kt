package com.mab.f2mbox.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * F2M Box brand theme, ported from the TV theme: near-black backgrounds
 * with cinematic emerald/teal accents, always dark. TV-Material's
 * border/borderVariant map onto Material 3's outline/outlineVariant.
 */
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
        outline = SurfaceNavyVariant,
        outlineVariant = BorderNavy,
        scrim = BgNavy
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = f2mBoxTypography(),
        content = content
    )
}
