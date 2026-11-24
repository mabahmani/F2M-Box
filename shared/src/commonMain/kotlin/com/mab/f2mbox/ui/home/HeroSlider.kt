package com.mab.f2mbox.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.mab.f2mbox.data.remote.HeroSlide

/**
 * Full-bleed hero slider. Platform actuals differ for TV D-pad vs desktop
 * pointer and edge taps.
 */
@Composable
expect fun HeroSlider(
    items: List<HeroSlide>,
    focusRequester: FocusRequester,
    onSlideClick: (String) -> Unit = {},
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
)