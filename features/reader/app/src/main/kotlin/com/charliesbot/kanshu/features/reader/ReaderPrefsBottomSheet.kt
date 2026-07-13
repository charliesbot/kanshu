package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderMargins
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.ui.components.KanshuDivider
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.navigator.ReaderLayoutDiagnostics
import com.charliesbot.kanshu.navigator.model.ParseDiagnostics
import com.charliesbot.kanshu.strings.R

data class ReaderPrefsCallbacks(
  val onFontChange: (ReaderFont) -> Unit,
  val onFontScaleChange: (Float) -> Unit,
  val onMarginsChange: (ReaderMargins) -> Unit,
  val onAlignmentChange: (ReaderAlignment) -> Unit,
  val onLineSpacingChange: (Float) -> Unit,
  val onParagraphSpacingChange: (Float) -> Unit,
  val onWordSpacingChange: (Float) -> Unit,
  val onLetterSpacingChange: (Float) -> Unit,
  val onResetSpacing: () -> Unit,
)

@Composable
fun ReaderPrefsBottomSheet(
  prefs: ReaderPreferences,
  callbacks: ReaderPrefsCallbacks,
  parseDiagnostics: ParseDiagnostics = ParseDiagnostics(),
  layoutDiagnostics: ReaderLayoutDiagnostics? = null,
  modifier: Modifier = Modifier,
) {
  var activeTab by remember { mutableStateOf(PrefsTab.Font) }
  Column(modifier.fillMaxWidth().heightIn(min = 500.dp)) {
    TabStrip(activeTab = activeTab, onSelect = { activeTab = it })
    KanshuDivider()
    when (activeTab) {
      PrefsTab.Font ->
        FontTab(
          prefs = prefs,
          onFontChange = callbacks.onFontChange,
          onFontScaleChange = callbacks.onFontScaleChange,
        )
      PrefsTab.Layout ->
        LayoutTab(
          margins = prefs.margins,
          alignment = prefs.alignment,
          onMarginsChange = callbacks.onMarginsChange,
          onAlignmentChange = callbacks.onAlignmentChange,
        )
      PrefsTab.Spacing ->
        SpacingTab(
          prefs = prefs,
          onLineSpacingChange = callbacks.onLineSpacingChange,
          onParagraphSpacingChange = callbacks.onParagraphSpacingChange,
          onWordSpacingChange = callbacks.onWordSpacingChange,
          onLetterSpacingChange = callbacks.onLetterSpacingChange,
          onResetSpacing = callbacks.onResetSpacing,
        )
      PrefsTab.Themes -> Box(Modifier.fillMaxWidth().height(96.dp))
      PrefsTab.More ->
        ReaderDiagnosticsTab(
          parseDiagnostics = parseDiagnostics,
          layoutDiagnostics = layoutDiagnostics,
        )
    }
  }
}

private enum class PrefsTab(val labelRes: Int) {
  Font(R.string.reader_prefs_tab_font),
  Layout(R.string.reader_prefs_tab_layout),
  Spacing(R.string.reader_prefs_tab_spacing),
  Themes(R.string.reader_prefs_tab_themes),
  More(R.string.reader_prefs_tab_more),
}

@Composable
private fun TabStrip(activeTab: PrefsTab, onSelect: (PrefsTab) -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 24.dp).horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(32.dp),
  ) {
    PrefsTab.entries.forEach { tab ->
      val active = tab == activeTab
      Column(
        Modifier.width(IntrinsicSize.Min).clickable { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        KanshuText(
          text = stringResource(tab.labelRes),
          style = KanshuTheme.typography.titleMedium,
          modifier = Modifier.padding(vertical = 16.dp),
        )
        Box(
          Modifier.fillMaxWidth()
            .height(3.dp)
            .background(if (active) KanshuTheme.colors.onBackground else Color.Transparent)
        )
      }
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReaderPrefsBottomSheetPreview() {
  KanshuTheme {
    ReaderPrefsBottomSheet(
      prefs = ReaderPreferences(font = ReaderFont.Literata, fontScale = 1.2f),
      callbacks =
        ReaderPrefsCallbacks(
          onFontChange = {},
          onFontScaleChange = {},
          onMarginsChange = {},
          onAlignmentChange = {},
          onLineSpacingChange = {},
          onParagraphSpacingChange = {},
          onWordSpacingChange = {},
          onLetterSpacingChange = {},
          onResetSpacing = {},
        ),
    )
  }
}
