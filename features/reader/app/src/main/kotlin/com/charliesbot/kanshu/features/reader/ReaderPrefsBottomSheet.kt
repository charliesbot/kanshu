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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderMargins
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.ui.components.KanshuDivider
import com.charliesbot.kanshu.core.ui.components.KanshuSlider
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R
import java.io.File
import kotlin.math.roundToInt

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
      PrefsTab.Themes,
      PrefsTab.More -> Box(Modifier.fillMaxWidth().height(96.dp))
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

@Composable
private fun FontTab(
  prefs: ReaderPreferences,
  onFontChange: (ReaderFont) -> Unit,
  onFontScaleChange: (Float) -> Unit,
) {
  Column(Modifier.fillMaxWidth()) {
    Row(
      Modifier.fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      ReaderFont.entries.forEach { font ->
        FontChip(font = font, selected = font == prefs.font, onClick = { onFontChange(font) })
      }
    }
    KanshuDivider()
    val totalStops =
      ((ReaderPreferences.SCALE_MAX - ReaderPreferences.SCALE_MIN) / ReaderPreferences.SCALE_STEP)
        .roundToInt() + 1
    KanshuSlider(
      value = prefs.fontScale,
      onValueChange = onFontScaleChange,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
      valueRange = ReaderPreferences.SCALE_MIN..ReaderPreferences.SCALE_MAX,
      steps = (totalStops - 2).coerceAtLeast(0),
      leading = {
        val description = stringResource(R.string.reader_prefs_font_size_smaller)
        Box(Modifier.clearAndSetSemantics { contentDescription = description }) {
          KanshuText(
            text = stringResource(R.string.reader_prefs_font_sample).take(1),
            style = KanshuTheme.typography.bodyMedium,
          )
        }
      },
      trailing = {
        val description = stringResource(R.string.reader_prefs_font_size_larger)
        Box(Modifier.clearAndSetSemantics { contentDescription = description }) {
          KanshuText(
            text = stringResource(R.string.reader_prefs_font_sample).take(1),
            style = KanshuTheme.typography.headlineMedium,
          )
        }
      },
    )
  }
}

@Composable
private fun FontChip(font: ReaderFont, selected: Boolean, onClick: () -> Unit) {
  val context = LocalContext.current
  val isPreview = LocalInspectionMode.current
  val sampleFontFamily =
    remember(font, isPreview) {
      if (isPreview) {
        FontFamily.Default
      } else {
        val cached = File(context.cacheDir, "font-preview-${font.name}")
        if (!cached.exists()) {
          context.assets.open(font.regularAssetPath).use { input ->
            cached.outputStream().use { output -> input.copyTo(output) }
          }
        }
        FontFamily(Font(cached))
      }
    }
  Column(
    Modifier.width(IntrinsicSize.Max).clickable(onClick = onClick).padding(4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier.height(40.dp).widthIn(min = 50.dp, max = 100.dp),
      contentAlignment = Alignment.Center,
    ) {
      KanshuText(
        text = stringResource(R.string.reader_prefs_font_sample),
        style =
          KanshuTheme.typography.headlineMedium.copy(
            fontFamily = sampleFontFamily,
            fontSize = if (font == ReaderFont.OpenDyslexic) 18.sp else 22.sp,
            lineHeight = 28.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
          ),
      )
    }
    KanshuText(
      text = font.displayName,
      style = KanshuTheme.typography.labelMedium.copy(fontSize = 14.sp),
    )
    Box(
      Modifier.fillMaxWidth()
        .padding(top = 6.dp)
        .height(3.dp)
        .background(if (selected) KanshuTheme.colors.onBackground else Color.Transparent)
    )
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
