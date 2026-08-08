package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReaderScreen(seriesId: Int, title: String, viewModel: ReaderViewModel = koinViewModel()) {
  LaunchedEffect(seriesId) { viewModel.open(seriesId) }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  when (val state = uiState) {
    ReaderUiState.Loading ->
      ReaderStatusMessage(message = stringResource(R.string.reader_status_loading))
    ReaderUiState.Error.NotFound ->
      ReaderStatusMessage(message = stringResource(R.string.reader_error_not_found))
    ReaderUiState.Error.OpenFailed ->
      ReaderStatusMessage(message = stringResource(R.string.reader_error_parse_failed))

    is ReaderUiState.Reading ->
      ReaderReadingContent(
        seriesId = seriesId,
        title = title,
        state = state,
        viewModel = viewModel,
      )
  }
}

@Composable
private fun ReaderStatusMessage(message: String) {
  KanshuScaffold {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
      KanshuText(text = message, style = KanshuTheme.typography.titleLarge)
    }
  }
}
