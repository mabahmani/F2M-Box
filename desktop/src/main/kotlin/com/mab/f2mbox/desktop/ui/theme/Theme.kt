package com.mab.f2mbox.desktop.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
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
        // Material hover ripples. Hover matches focus via shared [rememberCardHighlight].
        val scrollbarStyle = rememberScrollbarStyle()
        CompositionLocalProvider(
            LocalIndication provides NoIndication,
            LocalScrollbarStyle provides scrollbarStyle,
        ) {
            content()
        }
    }
}

@Composable
private fun rememberScrollbarStyle(): ScrollbarStyle {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return remember(onSurface) {
        ScrollbarStyle(
            minimalHeight = 28.dp,
            thickness = 10.dp,
            shape = RoundedCornerShape(5.dp),
            hoverDurationMillis = 120,
            unhoverColor = onSurface.copy(alpha = 0.42f),
            hoverColor = onSurface.copy(alpha = 0.78f),
        )
    }
}
