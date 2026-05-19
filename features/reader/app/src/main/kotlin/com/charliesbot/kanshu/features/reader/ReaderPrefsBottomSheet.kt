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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.ui.components.KanshuDivider
import com.charliesbot.kanshu.core.ui.components.KanshuSlider
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R
import kotlin.math.roundToInt

// Reader preferences sheet body. Lives in the reader feature because the prefs surface — fonts,
// font size — is reader-specific; KanshuBottomSheet stays a reusable design-system primitive
// that just gives this composable a chrome to sit in. Renders the four-tab strip from the
// design (Font / Layout / Themes / More) but only the Font tab has content for now; the other
// three are visible placeholders so the next PR can fill them in without re-introducing the
// tab UI.
@Composable
fun ReaderPrefsBottomSheet(
  prefs: ReaderPreferences,
  onFontChange: (ReaderFont) -> Unit,
  onFontScaleChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  var activeTab by remember { mutableStateOf(PrefsTab.Font) }
  Column(modifier.fillMaxWidth()) {
    TabStrip(activeTab = activeTab, onSelect = { activeTab = it })
    KanshuDivider()
    when (activeTab) {
      PrefsTab.Font ->
        FontTab(prefs = prefs, onFontChange = onFontChange, onFontScaleChange = onFontScaleChange)
      PrefsTab.Layout,
      PrefsTab.Themes,
      PrefsTab.More -> Box(Modifier.fillMaxWidth().height(96.dp))
    }
  }
}

private enum class PrefsTab(val labelRes: Int) {
  Font(R.string.reader_prefs_tab_font),
  Layout(R.string.reader_prefs_tab_layout),
  Themes(R.string.reader_prefs_tab_themes),
  More(R.string.reader_prefs_tab_more),
}

@Composable
private fun TabStrip(activeTab: PrefsTab, onSelect: (PrefsTab) -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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
          style =
            KanshuTheme.typography.titleMedium.copy(
              fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            ),
          modifier = Modifier.padding(vertical = 16.dp),
        )
        Box(
          Modifier.fillMaxWidth()
            .height(2.dp)
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
    // Steps math: discrete stops from SCALE_MIN..SCALE_MAX in SCALE_STEP increments. Slider's
    // `steps` is the count of stops *between* endpoints, so we subtract one then drop the two
    // endpoints (-2 + 1 = -1).
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
        // clearAndSetSemantics so TalkBack reads the descriptive string instead of "A".
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

// Renders an "Aa" preview + the font's display name. We deliberately don't render the preview
// in the actual ReaderFont — the bundled fonts live in assets/fonts/ (so Readium can serve them
// over its WebView), not res/font/, and pulling them into Compose-side FontFamily resources
// would duplicate the binaries. The label under the preview already tells the user what they'll
// get, which is the same model Kindle uses when its preview can't render the user's choice.
@Composable
private fun FontChip(font: ReaderFont, selected: Boolean, onClick: () -> Unit) {
  Column(
    Modifier.width(IntrinsicSize.Min).clickable(onClick = onClick).padding(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    KanshuText(
      text = stringResource(R.string.reader_prefs_font_sample),
      style =
        KanshuTheme.typography.headlineMedium.copy(
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        ),
    )
    KanshuText(
      text = font.displayName,
      style =
        KanshuTheme.typography.labelMedium.copy(
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        ),
      modifier = Modifier.padding(top = 4.dp),
    )
    Box(
      Modifier.fillMaxWidth()
        .padding(top = 4.dp)
        .height(2.dp)
        .background(if (selected) KanshuTheme.colors.onBackground else Color.Transparent)
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReaderPrefsBottomSheetPreview() {
  KanshuTheme {
    ReaderPrefsBottomSheet(
      prefs = ReaderPreferences(font = ReaderFont.NotoSerif, fontScale = 1.2f),
      onFontChange = {},
      onFontScaleChange = {},
    )
  }
}
