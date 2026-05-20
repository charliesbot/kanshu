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
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.ui.components.KanshuDivider
import com.charliesbot.kanshu.core.ui.components.KanshuSlider
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R
import java.io.File
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
        // Active state is signalled by the underline below; no FontWeight swap to
        // avoid e-ink ghosting on tab change.
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

// Renders an "Aa" preview in the font itself plus the display name below. Compose's `Font(file)`
// factory needs a real File on disk, but the bundled faces live in the APK's `assets/` so
// Readium can serve them over the WebView. We bridge by copying each asset into the cache
// directory once (cheap — small files, idempotent thanks to the exists() check) and pointing
// Compose at the cached File. Italic + bold faces aren't loaded here — the chip only needs to
// show what reading in this typeface will roughly look like, and the regular weight is the most
// representative preview.
//
// In `@Preview`, `LocalInspectionMode` is true and `Context.cacheDir` returns a path the
// preview sandbox can't write to. We short-circuit to the system default so the preview still
// lays out — the chip won't show the actual face, but the surrounding layout is what previews
// are useful for anyway.
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
    // Selection underline. 3dp so it reads as a deliberate indicator on e-ink (2dp is at
    // the edge of perceptible at typical Boox densities). Sits close to the label so the
    // pairing is obvious.
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
      onFontChange = {},
      onFontScaleChange = {},
    )
  }
}
