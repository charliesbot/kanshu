package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReaderScreen(seriesId: Int, title: String, viewModel: ReaderViewModel = koinViewModel()) {
  LaunchedEffect(seriesId) { viewModel.open(seriesId) }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  KanshuScaffold {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
      val message =
        when (uiState) {
          ReaderUiState.Loading -> "Loading..."
          is ReaderUiState.Ready -> title
          is ReaderUiState.Error -> "Failed to open book"
        }
      KanshuText(text = message, style = KanshuTheme.typography.titleLarge)
    }
  }
}
