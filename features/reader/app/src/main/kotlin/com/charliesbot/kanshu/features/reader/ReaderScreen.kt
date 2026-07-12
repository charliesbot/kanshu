package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.ui.components.KanshuBottomSheet
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.navigator.ReaderLayoutDiagnostics
import com.charliesbot.kanshu.navigator.ReaderPageViewer
import com.charliesbot.kanshu.strings.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReaderScreen(seriesId: Int, title: String, viewModel: ReaderViewModel = koinViewModel()) {
  LaunchedEffect(seriesId) { viewModel.open(seriesId) }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
  val pageCount by viewModel.pageCount.collectAsStateWithLifecycle()
  val resourceLoader by viewModel.resourceLoader.collectAsStateWithLifecycle()
  val preferences = remember { ReaderPreferences() }
  var previewPreferences by remember { mutableStateOf(preferences) }
  var overlayVisible by remember { mutableStateOf(false) }
  var readerPrefsVisible by remember { mutableStateOf(false) }
  var layoutDiagnostics by remember { mutableStateOf<ReaderLayoutDiagnostics?>(null) }
  var selectedText by remember { mutableStateOf<ReaderSelectedText?>(null) }

  when (val state = uiState) {
    ReaderUiState.Loading ->
      ReaderStatusMessage(message = stringResource(R.string.reader_status_loading))
    ReaderUiState.Error.NotFound ->
      ReaderStatusMessage(message = stringResource(R.string.reader_error_not_found))
    ReaderUiState.Error.OpenFailed ->
      ReaderStatusMessage(message = stringResource(R.string.reader_error_parse_failed))

    is ReaderUiState.Reading -> {
      LaunchedEffect(state.spineIndex) { layoutDiagnostics = null }
      Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        key(state.spineIndex) {
          ReaderPageViewer(
            document = state.document,
            preferences = preferences,
            currentPage = currentPage,
            onPageCount = { count -> viewModel.onPageCount(state.spineIndex, count) },
            resourceLoader = resourceLoader,
            onLayoutDiagnostics = { diagnostics -> layoutDiagnostics = diagnostics },
            onLayoutFailed = viewModel::onLayoutFailed,
            onPreviousPage = {
              overlayVisible = false
              viewModel.previousPage()
            },
            onCenterTap = { overlayVisible = true },
            onNextPage = {
              overlayVisible = false
              viewModel.nextPage()
            },
            onTextSelected = { text, anchor ->
              overlayVisible = false
              readerPrefsVisible = false
              selectedText = ReaderSelectedText(text = text, anchor = anchor)
            },
            onSelectionCleared = { selectedText = null },
            modifier = Modifier.fillMaxSize(),
          )
        }
        selectedText?.let { selection -> ReaderSelectionPopup(selection) }
        if (overlayVisible) {
          ReaderOverlay(
            title = title,
            pageLabel =
              stringResource(
                R.string.reader_overlay_page_label,
                // currentPage may hold the last-page sentinel until pagination reports a count.
                currentPage.coerceIn(0, pageCount.coerceAtLeast(1) - 1) + 1,
                pageCount.coerceAtLeast(1),
              ),
            onOpenReaderPrefs = {
              overlayVisible = false
              readerPrefsVisible = true
            },
            onDismiss = { overlayVisible = false },
          )
        }
        KanshuBottomSheet(isOpen = readerPrefsVisible, onDismiss = { readerPrefsVisible = false }) {
          ReaderPrefsBottomSheet(
            prefs = previewPreferences,
            callbacks =
              ReaderPrefsCallbacks(
                onFontChange = { previewPreferences = previewPreferences.copy(font = it) },
                onFontScaleChange = {
                  previewPreferences =
                    previewPreferences.copy(
                      fontScale =
                        it.coerceIn(ReaderPreferences.SCALE_MIN, ReaderPreferences.SCALE_MAX)
                    )
                },
                onMarginsChange = { previewPreferences = previewPreferences.copy(margins = it) },
                onAlignmentChange = {
                  previewPreferences = previewPreferences.copy(alignment = it)
                },
                onLineSpacingChange = {
                  previewPreferences =
                    previewPreferences.copy(
                      lineSpacing =
                        it.coerceIn(
                          ReaderPreferences.LINE_SPACING_MIN,
                          ReaderPreferences.LINE_SPACING_MAX,
                        )
                    )
                },
                onParagraphSpacingChange = {
                  previewPreferences =
                    previewPreferences.copy(
                      paragraphSpacing =
                        it.coerceIn(
                          ReaderPreferences.PARAGRAPH_SPACING_MIN,
                          ReaderPreferences.PARAGRAPH_SPACING_MAX,
                        )
                    )
                },
                onWordSpacingChange = {
                  previewPreferences =
                    previewPreferences.copy(
                      wordSpacing =
                        it.coerceIn(
                          ReaderPreferences.WORD_SPACING_MIN,
                          ReaderPreferences.WORD_SPACING_MAX,
                        )
                    )
                },
                onLetterSpacingChange = {
                  previewPreferences =
                    previewPreferences.copy(
                      letterSpacing =
                        it.coerceIn(
                          ReaderPreferences.LETTER_SPACING_MIN,
                          ReaderPreferences.LETTER_SPACING_MAX,
                        )
                    )
                },
                onResetSpacing = {
                  previewPreferences =
                    previewPreferences.copy(
                      lineSpacing = ReaderPreferences.LINE_SPACING_DEFAULT,
                      paragraphSpacing = ReaderPreferences.PARAGRAPH_SPACING_DEFAULT,
                      wordSpacing = ReaderPreferences.WORD_SPACING_DEFAULT,
                      letterSpacing = ReaderPreferences.LETTER_SPACING_DEFAULT,
                    )
                },
              ),
            parseDiagnostics = state.diagnostics,
            layoutDiagnostics = layoutDiagnostics,
          )
        }
      }
    }
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
