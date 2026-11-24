package com.mab.f2mbox.desktop.ui.theme

import androidx.compose.foundation.LocalIndication
import com.mab.f2mbox.ui.theme.BorderNavy
import com.mab.f2mbox.ui.theme.BgNavy
import com.mab.f2mbox.ui.theme.BrandBlue
import com.mab.f2mbox.ui.theme.BrandBlueDeep
import com.mab.f2mbox.ui.theme.BrandPurple
import com.mab.f2mbox.ui.theme.BrandPurpleDeep
import com.mab.f2mbox.ui.theme.SurfaceNavy
import com.mab.f2mbox.ui.theme.SurfaceNavyVariant
import com.mab.f2mbox.ui.theme.TextBright
import com.mab.f2mbox.ui.theme.TextMuted
import androidx.compose.material3.MaterialTheme
import com.mab.f2mbox.desktop.ui.common.NoIndication
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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
    ) {
        // Desktop uses custom focus cues (scale, border, primary fill), not
        // Material hover ripples. Hover matches focus via [rememberDesktopHighlight].
        CompositionLocalProvider(LocalIndication provides NoIndication) {
            content()
        }
    }
}
