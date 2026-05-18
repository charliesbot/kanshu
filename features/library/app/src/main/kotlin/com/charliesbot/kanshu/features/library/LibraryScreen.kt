package com.charliesbot.kanshu.features.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charliesbot.kanshu.core.library.DownloadState
import com.charliesbot.kanshu.core.library.LibraryItem
import com.charliesbot.kanshu.core.ui.components.KanshuCheckBadge
import com.charliesbot.kanshu.core.ui.components.KanshuCover
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryScreen(
  onItemClick: (LibraryItem) -> Unit = {},
  viewModel: LibraryViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val options by viewModel.options.collectAsStateWithLifecycle()

  LaunchedEffect(viewModel) { viewModel.navigate.collect { onItemClick(it) } }

  LibraryContent(
    uiState = uiState,
    onItemTap = viewModel::onItemTap,
    onItemLongPress = viewModel::onItemLongPress,
  )

  options?.let { item ->
    BookOptionsDialog(
      item = item,
      onDelete = viewModel::confirmDeleteDownload,
      onDismiss = viewModel::dismissOptions,
    )
  }
}

@Composable
private fun LibraryContent(
  uiState: LibraryUiState,
  onItemTap: (LibraryItem) -> Unit,
  onItemLongPress: (LibraryItem) -> Unit,
) {
  KanshuScaffold {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      KanshuText(
        text = stringResource(R.string.library_title),
        style = KanshuTheme.typography.titleLarge,
      )
      when (uiState) {
        is LibraryUiState.Loaded ->
          CoverGrid(items = uiState.items, onItemTap = onItemTap, onItemLongPress = onItemLongPress)

        else -> StatusText(uiState)
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverGrid(
  items: List<LibraryItem>,
  onItemTap: (LibraryItem) -> Unit,
  onItemLongPress: (LibraryItem) -> Unit,
) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 198.dp),
    contentPadding = PaddingValues(0.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    items(items = items, key = { it.id }) { item ->
      val interactionSource = remember { MutableInteractionSource() }
      KanshuCover(
        imageUrl = item.coverUrl,
        contentDescription = stringResource(R.string.library_cover_content_description, item.title),
        modifier =
          Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { onItemTap(item) },
            onLongClick = { onItemLongPress(item) },
          ),
      ) {
        DownloadStateOverlay(state = item.downloadState)
      }
    }
  }
}

@Composable
private fun BoxScope.DownloadStateOverlay(state: DownloadState) {
  when (state) {
    DownloadState.NotDownloaded -> Unit
    is DownloadState.Downloading ->
      OverlayLabel(
        text = stringResource(R.string.library_downloading_progress, state.progress),
        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
      )

    DownloadState.Downloaded ->
      KanshuCheckBadge(
        contentDescription = stringResource(R.string.library_downloaded_indicator),
        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
      )
  }
}

@Composable
private fun OverlayLabel(
  text: String,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
) {
  val semanticsModifier =
    if (contentDescription != null) {
      Modifier.semantics { this.contentDescription = contentDescription }
    } else {
      Modifier
    }
  KanshuText(
    text = text,
    style = KanshuTheme.typography.bodyLarge,
    modifier =
      modifier
        .background(KanshuTheme.colors.background)
        .border(1.dp, KanshuTheme.colors.border)
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .then(semanticsModifier),
  )
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
  KanshuText(
    text = text,
    style = KanshuTheme.typography.bodyLarge,
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
            listOf(
              LibraryItem(
                id = 1,
                title = "Downloaded",
                coverUrl = null,
                downloadState = DownloadState.Downloaded,
              ),
              LibraryItem(
                id = 2,
                title = "Downloading",
                coverUrl = null,
                downloadState = DownloadState.Downloading(progress = 42),
              ),
              LibraryItem(id = 3, title = "Not yet", coverUrl = null),
            )
        ),
      onItemTap = {},
      onItemLongPress = {},
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun LibraryScreenLoadingPreview() {
  KanshuTheme {
    LibraryContent(uiState = LibraryUiState.Loading, onItemTap = {}, onItemLongPress = {})
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun LibraryScreenEmptyPreview() {
  KanshuTheme {
    LibraryContent(uiState = LibraryUiState.Empty, onItemTap = {}, onItemLongPress = {})
  }
}
