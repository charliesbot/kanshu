package com.charliesbot.kanshu.features.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charliesbot.kanshu.core.library.LibraryItem
import com.charliesbot.kanshu.core.ui.components.KanshuCover
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = koinViewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  LibraryContent(uiState = uiState)
}

@Composable
private fun LibraryContent(uiState: LibraryUiState) {
  KanshuScaffold {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      BasicText(
        text = stringResource(R.string.library_title),
        style = KanshuTheme.typography.title.copy(color = KanshuTheme.colors.onBackground),
      )
      when (uiState) {
        is LibraryUiState.Loaded -> CoverGrid(items = uiState.items)
        else -> StatusText(uiState)
      }
    }
  }
}

@Composable
private fun CoverGrid(items: List<LibraryItem>) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 128.dp),
    contentPadding = PaddingValues(0.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    items(items = items, key = { it.id }) { item ->
      KanshuCover(
        imageUrl = item.coverUrl,
        contentDescription = stringResource(R.string.library_cover_content_description, item.title),
      )
    }
  }
}

@Composable
private fun StatusText(uiState: LibraryUiState) {
  val text =
    when (uiState) {
      LibraryUiState.Loading -> stringResource(R.string.library_status_loading)
      LibraryUiState.Empty -> stringResource(R.string.library_status_empty)
      LibraryUiState.NoCredentials -> stringResource(R.string.library_status_no_credentials)
      LibraryUiState.Error.Unauthorized -> stringResource(R.string.library_error_unauthorized)
      LibraryUiState.Error.Network -> stringResource(R.string.library_error_network)
      LibraryUiState.Error.UnexpectedResponse ->
        stringResource(R.string.library_error_unexpected_response)
      LibraryUiState.Error.Unknown -> stringResource(R.string.library_error_unknown)
      is LibraryUiState.Loaded -> return
    }
  BasicText(
    text = text,
    style = KanshuTheme.typography.body.copy(color = KanshuTheme.colors.onBackground),
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
  )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun LibraryScreenLoadedPreview() {
  KanshuTheme {
    LibraryContent(
      uiState =
        LibraryUiState.Loaded(
          items =
            (1..6).map {
              LibraryItem(id = it, title = "Book $it", coverUrl = "https://example.com/cover/$it")
            }
        )
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun LibraryScreenLoadingPreview() {
  KanshuTheme { LibraryContent(uiState = LibraryUiState.Loading) }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun LibraryScreenEmptyPreview() {
  KanshuTheme { LibraryContent(uiState = LibraryUiState.Empty) }
}
