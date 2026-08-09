package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charliesbot.kanshu.navigator.ReaderHighlightTap
import com.charliesbot.kanshu.navigator.ReaderImageCache
import com.charliesbot.kanshu.navigator.ReaderLayoutDiagnostics
import com.charliesbot.kanshu.navigator.ReaderSelectionInfo

@Composable
internal fun ReaderReadingContent(
  seriesId: Int,
  title: String,
  state: ReaderUiState.Reading,
  viewModel: ReaderViewModel,
) {
  val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
  val pageCount by viewModel.pageCount.collectAsStateWithLifecycle()
  val resourceLoader by viewModel.resourceLoader.collectAsStateWithLifecycle()
  // Hoisted above key(chapterToken) so decoded images survive chapter changes.
  val imageCache = remember(seriesId) { ReaderImageCache() }
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val highlights by viewModel.highlights.collectAsStateWithLifecycle()
  var transientUi by remember { mutableStateOf<ReaderTransientUi>(ReaderTransientUi.None) }
  var layoutDiagnostics by
    remember(state.chapterToken) {
      mutableStateOf<ReaderLayoutDiagnostics?>(null)
    }
  var clearSelectionToken by remember { mutableStateOf(0) }

  Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
    ReaderPageContent(
      state = state,
      viewModel = viewModel,
      preferences = preferences,
      currentPage = currentPage,
      resourceLoader = resourceLoader,
      highlights = highlights,
      clearSelectionToken = clearSelectionToken,
      imageCache = imageCache,
      onLayoutDiagnostics = { diagnostics -> layoutDiagnostics = diagnostics },
      onPreviousPage = {
        transientUi = ReaderTransientUi.None
        viewModel.previousPage()
      },
      onCenterTap = { transientUi = ReaderTransientUi.Overlay },
      onNextPage = {
        transientUi = ReaderTransientUi.None
        viewModel.nextPage()
      },
      onTextSelected = { info ->
        transientUi = ReaderTransientUi.Selection(info)
      },
      onHighlightTapped = { tap ->
        transientUi = ReaderTransientUi.Highlight(tap)
      },
      onSelectionCleared = {
        if (transientUi is ReaderTransientUi.Selection) {
          transientUi = ReaderTransientUi.None
        }
      },
      onLinkTapped = {
        if (transientUi is ReaderTransientUi.Overlay) {
          transientUi = ReaderTransientUi.None
        }
      },
    )
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
    ReaderChrome(
      title = title,
      currentPage = currentPage,
      pageCount = pageCount,
      overlayVisible = transientUi is ReaderTransientUi.Overlay,
      readerPrefsVisible = transientUi is ReaderTransientUi.Preferences,
      preferences = preferences,
      parseDiagnostics = state.diagnostics,
      layoutDiagnostics = layoutDiagnostics,
      preferencesCallbacks =
        // Changes apply live and persist; the repository clamps ranges and the viewer
        // repaginates behind the sheet.
        ReaderPrefsCallbacks(
          onFontChange = viewModel::setFont,
          onFontScaleChange = viewModel::setFontScale,
          onBoldnessChange = viewModel::setBoldness,
          onMarginsChange = viewModel::setMargins,
          onAlignmentChange = viewModel::setAlignment,
          onLineSpacingChange = viewModel::setLineSpacing,
          onParagraphSpacingChange = viewModel::setParagraphSpacing,
          onWordSpacingChange = viewModel::setWordSpacing,
          onLetterSpacingChange = viewModel::setLetterSpacing,
          onResetSpacing = viewModel::resetSpacing,
        ),
      onOpenReaderPrefs = {
        transientUi = ReaderTransientUi.Preferences
      },
      onDismissOverlay = {
        if (transientUi is ReaderTransientUi.Overlay) {
          transientUi = ReaderTransientUi.None
        }
      },
      onDismissReaderPrefs = {
        if (transientUi is ReaderTransientUi.Preferences) {
          transientUi = ReaderTransientUi.None
        }
      },
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
