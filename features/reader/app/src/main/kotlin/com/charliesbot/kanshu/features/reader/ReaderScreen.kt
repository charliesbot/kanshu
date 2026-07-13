package com.charliesbot.kanshu.features.reader

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charliesbot.kanshu.core.ui.components.KanshuBottomSheet
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.navigator.ReaderImageCache
import com.charliesbot.kanshu.navigator.ReaderLayoutDiagnostics
import com.charliesbot.kanshu.navigator.ReaderPageViewer
import com.charliesbot.kanshu.strings.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReaderScreen(seriesId: Int, title: String, viewModel: ReaderViewModel = koinViewModel()) {
  LaunchedEffect(seriesId) { viewModel.open(seriesId) }

  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
  val pageCount by viewModel.pageCount.collectAsStateWithLifecycle()
  val resourceLoader by viewModel.resourceLoader.collectAsStateWithLifecycle()
  // Hoisted above key(spineIndex) so decoded images survive chapter changes.
  val imageCache = remember(seriesId) { ReaderImageCache() }
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
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
            onLinkTapped = { href ->
              overlayVisible = false
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
            prefs = preferences,
            callbacks =
              // Changes apply live and persist; the repository clamps ranges and the viewer
              // repaginates behind the sheet.
              ReaderPrefsCallbacks(
                onFontChange = viewModel::setFont,
                onFontScaleChange = viewModel::setFontScale,
                onMarginsChange = viewModel::setMargins,
                onAlignmentChange = viewModel::setAlignment,
                onLineSpacingChange = viewModel::setLineSpacing,
                onParagraphSpacingChange = viewModel::setParagraphSpacing,
                onWordSpacingChange = viewModel::setWordSpacing,
                onLetterSpacingChange = viewModel::setLetterSpacing,
                onResetSpacing = viewModel::resetSpacing,
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
