package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
  // Hoisted above key(spineIndex) so decoded images survive chapter changes.
  val imageCache = remember(seriesId) { ReaderImageCache() }
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val highlights by viewModel.highlights.collectAsStateWithLifecycle()
  var overlayVisible by remember { mutableStateOf(false) }
  var readerPrefsVisible by remember { mutableStateOf(false) }
  var layoutDiagnostics by remember { mutableStateOf<ReaderLayoutDiagnostics?>(null) }
  var selectedText by remember { mutableStateOf<ReaderSelectionInfo?>(null) }
  var tappedHighlight by remember { mutableStateOf<ReaderHighlightTap?>(null) }
  var clearSelectionToken by remember { mutableStateOf(0) }

  LaunchedEffect(state.spineIndex) { layoutDiagnostics = null }
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
        overlayVisible = false
        tappedHighlight = null
        viewModel.previousPage()
      },
      onCenterTap = { overlayVisible = true },
      onNextPage = {
        overlayVisible = false
        tappedHighlight = null
        viewModel.nextPage()
      },
      onTextSelected = { info ->
        overlayVisible = false
        readerPrefsVisible = false
        tappedHighlight = null
        selectedText = info
      },
      onHighlightTapped = { tap ->
        overlayVisible = false
        readerPrefsVisible = false
        selectedText = null
        tappedHighlight = tap
      },
      onSelectionCleared = { selectedText = null },
      onLinkTapped = { overlayVisible = false },
    )
    ReaderHighlightPopups(
      selectedText = selectedText,
      tappedHighlight = tappedHighlight,
      onHighlightAdded = { selection, color ->
        viewModel.addHighlight(selection, color)
        // Drops the engine's selection too; onSelectionCleared closes the popup.
        clearSelectionToken++
      },
      onHighlightRemoved = { tap ->
        viewModel.removeHighlight(tap.highlight.id)
        tappedHighlight = null
      },
      onHighlightColorChanged = { tap, color ->
        viewModel.setHighlightColor(tap.highlight.id, color)
        tappedHighlight = null
      },
    )
    ReaderChrome(
      title = title,
      currentPage = currentPage,
      pageCount = pageCount,
      overlayVisible = overlayVisible,
      readerPrefsVisible = readerPrefsVisible,
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
        overlayVisible = false
        readerPrefsVisible = true
      },
      onDismissOverlay = { overlayVisible = false },
      onDismissReaderPrefs = { readerPrefsVisible = false },
    )
  }
}
