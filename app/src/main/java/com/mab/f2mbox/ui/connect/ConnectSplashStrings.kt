@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.connect

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.mab.f2mbox.R
import com.mab.f2mbox.connect.ConnectSplashStrings

@Composable
fun rememberConnectSplashStrings(): ConnectSplashStrings = ConnectSplashStrings(
    appName = stringResource(R.string.app_name),
    back = stringResource(R.string.back),
    retry = stringResource(R.string.retry),
    viewLogs = stringResource(R.string.view_logs),
    domainHint = stringResource(R.string.error_domain_hint),
    domainFieldPlaceholder = stringResource(R.string.error_domain_field),
    domainSubmit = stringResource(R.string.error_domain_submit),
    domainInvalidLabel = stringResource(R.string.error_domain_invalid),
)