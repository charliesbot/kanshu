package com.charliesbot.kanshu.features.reader

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charliesbot.kanshu.navigator.ReaderHighlightTap
import com.charliesbot.kanshu.navigator.ReaderImageCache
import com.charliesbot.kanshu.navigator.ReaderLayoutDiagnostics
import com.charliesbot.kanshu.navigator.ReaderPageViewer
import com.charliesbot.kanshu.navigator.ReaderSelectionInfo
import com.charliesbot.kanshu.strings.R

@Composable
internal fun ReaderReadingContent(
  bookId: String,
  title: String,
  state: ReaderUiState.Reading,
  viewModel: ReaderViewModel,
) {
  val context = LocalContext.current
  val pagination by viewModel.pagination.collectAsStateWithLifecycle()
  val resourceLoader by viewModel.resourceLoader.collectAsStateWithLifecycle()
  // Hoisted above key(chapterToken) so decoded images survive chapter changes.
  val imageCache = remember(bookId) { ReaderImageCache() }
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val highlights by viewModel.highlights.collectAsStateWithLifecycle()
  var transientUi by remember { mutableStateOf<ReaderTransientUi>(ReaderTransientUi.None) }
  var layoutDiagnostics by
    remember(state.chapterToken) {
      mutableStateOf<ReaderLayoutDiagnostics?>(null)
    }
  var clearSelectionToken by remember { mutableStateOf(0) }

  Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
    key(state.chapterToken) {
      ReaderPageViewer(
        document = state.document,
        preferences = preferences,
        currentPage = pagination.currentPage,
        onPageCount = { count -> viewModel.onPageCount(state.chapterToken, count) },
        onPagePositions = { positions ->
          viewModel.onPagePositions(state.chapterToken, positions)
        },
        resourceLoader = resourceLoader,
        onLayoutDiagnostics = { diagnostics -> layoutDiagnostics = diagnostics },
        onLayoutFailed = { viewModel.onLayoutFailed(state.chapterToken) },
        onPreviousPage = {
          transientUi = ReaderTransientUi.None
          viewModel.previousPage()
        },
        onCenterTap = { transientUi = ReaderTransientUi.Overlay },
        onNextPage = {
          transientUi = ReaderTransientUi.None
          viewModel.nextPage()
        },
        onTextSelected = { info -> transientUi = ReaderTransientUi.Selection(info) },
        highlights = highlights,
        onHighlightTapped = { tap -> transientUi = ReaderTransientUi.Highlight(tap) },
        clearSelectionToken = clearSelectionToken,
        onSelectionCleared = {
          if (transientUi is ReaderTransientUi.Selection) {
            transientUi = ReaderTransientUi.None
          }
        },
        onLinkTapped = { href ->
          if (transientUi is ReaderTransientUi.Overlay) {
            transientUi = ReaderTransientUi.None
          }
          if (href.startsWith("http://", true) || href.startsWith("https://", true)) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, href.toUri())) }
          } else {
            viewModel.openLink(href)
          }
        },
        imageCache = imageCache,
        modifier = Modifier.fillMaxSize(),
      )
    }

    ReaderHighlightPopups(
      selectedText = (transientUi as? ReaderTransientUi.Selection)?.info,
      tappedHighlight = (transientUi as? ReaderTransientUi.Highlight)?.tap,
      onHighlightAdded = { selection, color ->
        viewModel.addHighlight(selection, color)
        // Drops the engine's selection too; onSelectionCleared closes the popup.
        clearSelectionToken++
      },
      onHighlightRemoved = { tap ->
        viewModel.removeHighlight(tap.highlight.id)
        transientUi = ReaderTransientUi.None
      },
      onHighlightColorChanged = { tap, color ->
        viewModel.setHighlightColor(tap.highlight.id, color)
        transientUi = ReaderTransientUi.None
      },
    )

    if (transientUi is ReaderTransientUi.Overlay) {
      val pageCount = pagination.pageCount.coerceAtLeast(1)
      val currentPageNumber = pagination.currentPage.coerceIn(0, pageCount - 1) + 1
      ReaderOverlay(
        title = title,
        pageLabel =
          stringResource(R.string.reader_overlay_page_label, currentPageNumber, pageCount),
        onOpenReaderPrefs = { transientUi = ReaderTransientUi.Preferences },
        onDismiss = { transientUi = ReaderTransientUi.None },
      )
    }

    ReaderPrefsBottomSheet(
      isOpen = transientUi is ReaderTransientUi.Preferences,
      onDismiss = {
        if (transientUi is ReaderTransientUi.Preferences) {
          transientUi = ReaderTransientUi.None
        }
      },
      prefs = preferences,
      viewModel = viewModel,
      parseDiagnostics = state.diagnostics,
      layoutDiagnostics = layoutDiagnostics,
    )
  }
}

private sealed interface ReaderTransientUi {
  data object None : ReaderTransientUi

  data object Overlay : ReaderTransientUi

  data object Preferences : ReaderTransientUi

  data class Selection(val info: ReaderSelectionInfo) : ReaderTransientUi

  data class Highlight(val tap: ReaderHighlightTap) : ReaderTransientUi
}
