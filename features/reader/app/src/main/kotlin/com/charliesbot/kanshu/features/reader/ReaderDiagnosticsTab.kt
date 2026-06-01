package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.ui.components.KanshuDivider
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.navigator.ReaderLayoutDiagnostics
import com.charliesbot.kanshu.navigator.model.ParseDiagnostics
import com.charliesbot.kanshu.strings.R

@Composable
fun ReaderDiagnosticsTab(
  parseDiagnostics: ParseDiagnostics,
  layoutDiagnostics: ReaderLayoutDiagnostics?,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    KanshuText(
      text = stringResource(R.string.reader_diagnostics_title),
      style = KanshuTheme.typography.titleLarge,
    )
    ReaderDiagnosticSummary(layoutDiagnostics)
    KanshuDivider()
    ReaderDiagnosticTagSection(
      title = stringResource(R.string.reader_diagnostics_unsupported_block_tags),
      counts = parseDiagnostics.unsupportedBlockTags,
    )
    ReaderDiagnosticTagSection(
      title = stringResource(R.string.reader_diagnostics_unsupported_inline_tags),
      counts = parseDiagnostics.unsupportedInlineTags,
    )
  }
}

@Composable
private fun ReaderDiagnosticSummary(layoutDiagnostics: ReaderLayoutDiagnostics?) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    val blockCount = layoutDiagnostics?.blockCount ?: 0
    val pageCount = layoutDiagnostics?.pageCount ?: 0
    val paginationMillis = layoutDiagnostics?.paginationMillis ?: 0
    KanshuText(
      text = stringResource(R.string.reader_diagnostics_block_count, blockCount),
      style = KanshuTheme.typography.bodyLarge,
    )
    KanshuText(
      text = stringResource(R.string.reader_diagnostics_page_count, pageCount),
      style = KanshuTheme.typography.bodyLarge,
    )
    KanshuText(
      text = stringResource(R.string.reader_diagnostics_pagination_millis, paginationMillis),
      style = KanshuTheme.typography.bodyLarge,
    )
  }
}

@Composable
private fun ReaderDiagnosticTagSection(title: String, counts: Map<String, Int>) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    KanshuText(text = title, style = KanshuTheme.typography.titleMedium)
    if (counts.isEmpty()) {
      KanshuText(
        text = stringResource(R.string.reader_diagnostics_no_unsupported_tags),
        style = KanshuTheme.typography.bodyLarge,
      )
    } else {
      counts.toSortedMap().forEach { (tag, count) ->
        KanshuText(
          text = stringResource(R.string.reader_diagnostics_tag_count, tag, count),
          style = KanshuTheme.typography.bodyLarge,
        )
      }
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReaderDiagnosticsTabPreview() {
  KanshuTheme {
    ReaderDiagnosticsTab(
      parseDiagnostics =
        ParseDiagnostics(
          unsupportedBlockTags = mapOf("table" to 2, "aside" to 1),
          unsupportedInlineTags = mapOf("ruby" to 3),
        ),
      layoutDiagnostics =
        ReaderLayoutDiagnostics(blockCount = 128, pageCount = 42, paginationMillis = 86),
    )
  }
}
