package com.mab.f2mbox.ui.home

import com.mab.f2mbox.data.remote.HomeData

/** UI state of the home screen. */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Ready(val home: HomeData) : HomeUiState
}