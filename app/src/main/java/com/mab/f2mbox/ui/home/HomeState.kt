package com.mab.f2mbox.ui.home

import androidx.annotation.StringRes
import com.mab.f2mbox.data.remote.HomeData

/** UI state of the home screen. */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(@StringRes val messageRes: Int) : HomeUiState
    data class Ready(val home: HomeData) : HomeUiState
}
