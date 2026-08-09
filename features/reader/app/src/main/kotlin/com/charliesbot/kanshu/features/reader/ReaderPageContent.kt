package com.charliesbot.kanshu.features.reader

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.navigator.ReaderHighlight
import com.charliesbot.kanshu.navigator.ReaderHighlightTap
import com.charliesbot.kanshu.navigator.ReaderImageCache
import com.charliesbot.kanshu.navigator.ReaderLayoutDiagnostics
import com.charliesbot.kanshu.navigator.ReaderPageViewer
import com.charliesbot.kanshu.navigator.ReaderResourceLoader
import com.charliesbot.kanshu.navigator.ReaderSelectionInfo

@Composable
internal fun ReaderPageContent(
  state: ReaderUiState.Reading,
  viewModel: ReaderViewModel,
  preferences: ReaderPreferences,
  currentPage: Int,
  resourceLoader: ReaderResourceLoader?,
  highlights: List<ReaderHighlight>,
  clearSelectionToken: Int,
  imageCache: ReaderImageCache,
  onLayoutDiagnostics: (ReaderLayoutDiagnostics) -> Unit,
  onPreviousPage: () -> Unit,
  onCenterTap: () -> Unit,
  onNextPage: () -> Unit,
  onTextSelected: (ReaderSelectionInfo) -> Unit,
  onHighlightTapped: (ReaderHighlightTap) -> Unit,
  onSelectionCleared: () -> Unit,
  onLinkTapped: () -> Unit,
) {
  val context = LocalContext.current

  key(state.spineIndex) {
    ReaderPageViewer(
      document = state.document,
      preferences = preferences,
      currentPage = currentPage,
      onPageCount = { count -> viewModel.onPageCount(state.spineIndex, count) },
      onPagePositions = { positions -> viewModel.onPagePositions(state.spineIndex, positions) },
      resourceLoader = resourceLoader,
      onLayoutDiagnostics = onLayoutDiagnostics,
      onLayoutFailed = viewModel::onLayoutFailed,
      onPreviousPage = onPreviousPage,
      onCenterTap = onCenterTap,
      onNextPage = onNextPage,
      onTextSelected = onTextSelected,
      highlights = highlights,
      onHighlightTapped = onHighlightTapped,
      clearSelectionToken = clearSelectionToken,
      onSelectionCleared = onSelectionCleared,
      onLinkTapped = { href ->
        onLinkTapped()
        if (href.startsWith("http://", true) || href.startsWith("https://", true)) {
          // External link: hand off to the system browser; ignore if none exists.
          runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, href.toUri())) }
        } else {
          viewModel.openLink(href)
        }
      },
      imageCache = imageCache,
      modifier = Modifier.fillMaxSize(),
    )
  }
}
