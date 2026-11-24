@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.connect

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.mab.f2mbox.connect.ConnectState
import com.mab.f2mbox.ui.theme.F2MBoxTheme

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
fun SplashScreenPreview() {
    F2MBoxTheme {
        com.mab.f2mbox.ui.connect.SplashScreen(
            strings = rememberConnectSplashStrings(),
            state = ConnectState.Connecting,
            onBack = {},
            onRetry = {},
        )
    }
}