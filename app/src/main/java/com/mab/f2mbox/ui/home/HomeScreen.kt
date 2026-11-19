@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.mab.f2mbox.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mab.f2mbox.data.F2MRepository

/**
 * Home screen. Loads the homepage payload once and renders the hero slider;
 * the carousels below it come in a later pass.
 */
@Composable
fun HomeScreen(repository: F2MRepository, modifier: Modifier = Modifier) {
    var state by remember { mutableStateOf<HomeUiState>(HomeUiState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        state = HomeUiState.Loading
        repository.home().fold(
            onSuccess = { state = HomeUiState.Ready(it) },
            onFailure = {
                state = HomeUiState.Error(it.message ?: "Couldn't load the home feed")
            }
        )
    }

    when (val current = state) {
        HomeUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The server may take up to 2 minutes to wake up",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        is HomeUiState.Error -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = { attempt++ }) {
                    Text("Retry")
                }
            }
        }

        is HomeUiState.Ready -> HeroSlider(
            items = current.home.heroSlider,
            modifier = modifier
        )
    }
}
