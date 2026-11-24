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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.mab.f2mbox.ui.theme.f2mMaterial3DarkColorScheme

/**
 * F2M Box brand theme, ported from the TV theme: near-black backgrounds
 * with cinematic emerald/teal accents, always dark.
 */
@Composable
fun F2MBoxTheme(content: @Composable () -> Unit) {
    val colorScheme = f2mMaterial3DarkColorScheme()
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
