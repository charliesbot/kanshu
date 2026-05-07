package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charliesbot.kanshu.core.ui.components.KanshuButton
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// Reading mode defaults to zero persistent app UI per the PRD. The Prev/Next row is the V1
// navigation surface; it gets replaced by tap zones in a follow-up PR. Buttons turn pages
// (paginated WebView) and roll the chapter at boundaries — Prev on page 0 lands on the previous
// chapter's last page, Next on the last page lands on the next chapter's page 0.
@Composable
fun ReaderScreen(
  seriesId: Int,
  title: String,
  viewModel: ReaderViewModel = koinViewModel { parametersOf(seriesId) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  ReaderContent(
    uiState = uiState,
    fallbackTitle = title,
    onPrev = viewModel::goPrev,
    onNext = viewModel::goNext,
    onPageCount = viewModel::onPageCountReported,
  )
}

@Composable
private fun ReaderContent(
  uiState: ReaderUiState,
  fallbackTitle: String,
  onPrev: () -> Unit,
  onNext: () -> Unit,
  onPageCount: (Int) -> Unit,
) {
  KanshuScaffold {
    when (uiState) {
      ReaderUiState.Loading ->
        StatusText(text = fallbackTitle.ifBlank { stringResource(R.string.reader_status_loading) })
      is ReaderUiState.Ready ->
        ReaderBody(uiState = uiState, onPrev = onPrev, onNext = onNext, onPageCount = onPageCount)
      ReaderUiState.Error.NotFound ->
        StatusText(text = stringResource(R.string.reader_error_not_found))
      ReaderUiState.Error.ParseFailed ->
        StatusText(text = stringResource(R.string.reader_error_parse_failed))
      ReaderUiState.Error.ReadFailed ->
        StatusText(text = stringResource(R.string.reader_error_read_failed))
    }
  }
}

@Composable
private fun ReaderBody(
  uiState: ReaderUiState.Ready,
  onPrev: () -> Unit,
  onNext: () -> Unit,
  onPageCount: (Int) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    EpubWebView(
      html = uiState.currentHtml,
      currentPage = uiState.currentPageIndex,
      onPageCount = onPageCount,
      modifier = Modifier.weight(1f).fillMaxWidth(),
    )
    ChapterControls(uiState = uiState, onPrev = onPrev, onNext = onNext)
  }
}

@Composable
private fun ChapterControls(uiState: ReaderUiState.Ready, onPrev: () -> Unit, onNext: () -> Unit) {
  val hasMorePages = uiState.pageCount == null || uiState.currentPageIndex + 1 < uiState.pageCount
  val hasMoreChapters = uiState.currentChapterIndex + 1 < uiState.chapterCount
  val hasPrevPages = uiState.currentPageIndex > 0
  val hasPrevChapters = uiState.currentChapterIndex > 0
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    KanshuButton(
      text = stringResource(R.string.reader_prev),
      onClick = onPrev,
      enabled = hasPrevPages || hasPrevChapters,
    )
    if (uiState.pageCount != null) {
      BasicText(
        text =
          stringResource(
            R.string.reader_page_position,
            uiState.currentPageIndex + 1,
            uiState.pageCount,
          ),
        style = KanshuTheme.typography.body.copy(color = KanshuTheme.colors.onBackground),
      )
    }
    KanshuButton(
      text = stringResource(R.string.reader_next),
      onClick = onNext,
      enabled = hasMorePages || hasMoreChapters,
    )
  }
}

@Composable
private fun StatusText(text: String) {
  BasicText(
    text = text,
    style = KanshuTheme.typography.body.copy(color = KanshuTheme.colors.onBackground),
    modifier =
      Modifier.fillMaxSize().padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
  )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReaderScreenLoadingPreview() {
  KanshuTheme {
    ReaderContent(
      uiState = ReaderUiState.Loading,
      fallbackTitle = "Alice's Adventures in Wonderland",
      onPrev = {},
      onNext = {},
      onPageCount = {},
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReaderScreenErrorPreview() {
  KanshuTheme {
    ReaderContent(
      uiState = ReaderUiState.Error.ParseFailed,
      fallbackTitle = "",
      onPrev = {},
      onNext = {},
      onPageCount = {},
    )
  }
}
