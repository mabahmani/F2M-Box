package com.mab.f2mbox.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme

/** Brand dark scheme for shared CMP Material 3 composables. */
fun f2mMaterial3DarkColorScheme(): ColorScheme = darkColorScheme(
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
    scrim = BgNavy,
)