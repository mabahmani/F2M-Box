package com.mab.f2mbox.desktop.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mab.f2mbox.desktop.player.SubtitleCue
import com.mab.f2mbox.desktop.player.SubtitleStyle

/**
 * Pure Compose text subtitle overlay for Desktop video playback.
 *
 * Renders active [SubtitleCue] text directly in Compose with real-time
 * reactive [SubtitleStyle] updates (size, weight, color, background opacity,
 * edge shadow/outline, and bottom offset) without rebuilding libVLC.
 */
@Composable
fun DesktopSubtitleView(
    cue: SubtitleCue?,
    style: SubtitleStyle,
    modifier: Modifier = Modifier
) {
    if (cue == null || cue.text.isBlank()) return

    // Keep the app's subtitle font (the theme's Vazirmatn) while overriding
    // the size/weight/color the user picks; an explicit TextStyle would
    // otherwise fall back to the platform font.
    val fontFamily = LocalTextStyle.current.fontFamily

    val alpha = (style.backgroundOpacityPercent / 100f).coerceIn(0f, 1f)
    val bgColor = if (alpha > 0f) Color.Black.copy(alpha = alpha) else Color.Transparent
    val textColor = Color(style.colorRgb or 0xFF000000.toInt())
    val fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal

    val shadow = when (style.edge) {
        SubtitleStyle.Edge.None -> null
        SubtitleStyle.Edge.Shadow -> Shadow(
            color = Color.Black.copy(alpha = (style.edgeStrengthPercent / 100f).coerceIn(0f, 1f)),
            offset = Offset(2f, 2f),
            blurRadius = 3f
        )
        SubtitleStyle.Edge.Outline -> Shadow(
            color = Color.Black.copy(alpha = (style.edgeStrengthPercent / 100f).coerceIn(0f, 1f)),
            offset = Offset.Zero,
            blurRadius = 4f
        )
    }

    // Padding depends on the container height, so read it from the layout.
    // libVLC keeps a small baseline gap even at 0% offset (see
    // SubtitleStyle.bottomPaddingDp); the band height lets the preview scale
    // that gap and the user's offset the same way a real frame would.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bottomPadding = style.bottomPaddingDp(maxHeight.value).dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .background(bgColor, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = cue.text,
                    style = TextStyle(
                        color = textColor,
                        fontSize = style.textSizeSp.sp,
                        fontWeight = fontWeight,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center,
                        shadow = shadow
                    )
                )
            }
        }
    }
}
