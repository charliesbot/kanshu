package com.charliesbot.kanshu.features.reader

import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderMargins
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.ExperimentalReadiumApi

// We deliberately don't assert against EpubTypography.fragmentConfiguration here: materializing
// it routes through Readium's addSource → android.net.Uri path, which the JVM stub doesn't
// implement. The font-wiring regression that test would guard against is also a loud, on-device
// failure (books open with the system default instead of Noto Serif), so manual verification
// catches it instantly. If we ever add Robolectric for other reasons, fold a servedAssets
// assertion back in here.
@OptIn(ExperimentalReadiumApi::class)
class EpubTypographyTest {

  @Test
  fun `defaults honor publisher styles and enforce single column`() {
    // These two anchor the "layout-theirs, fonts-ours" model from docs/KINDLE_TYPOGRAPHY.md §5
    // — publisher CSS shapes the book; our EpubDefaults/RsProperties act as fallbacks. Flipping
    // either back would silently un-do the chosen model.
    assertTrue(EpubTypography.defaults.publisherStyles!!)
    assertEquals(ColumnCount.ONE, EpubTypography.defaults.columnCount)
  }

  @Test
  fun `default reader preferences map to the bundled serif font`() {
    val prefs = EpubTypography.toEpubPreferences(ReaderPreferences())
    // Workaround active: the resolved name carries the `-Kanshu` suffix so our injected
    // @font-face rule wins (see EpubFontInjector.kt). Drop the suffix here when Readium PR #787
    // ships and the suffix is removed in EpubTypography.readiumFamily.
    assertEquals("Literata-Kanshu", prefs.fontFamily?.name)
    assertEquals(1.0, prefs.fontSize!!, 0.0001)
  }

  @Test
  fun `reader preferences map margins and alignment to epub preferences`() {
    val defaultPrefs = EpubTypography.toEpubPreferences(ReaderPreferences())
    assertEquals(ReaderMargins.Medium.value, defaultPrefs.pageMargins!!, 0.0001)
    assertEquals(TextAlign.JUSTIFY, defaultPrefs.textAlign)

    val compactLeftPrefs =
      EpubTypography.toEpubPreferences(
        ReaderPreferences(margins = ReaderMargins.Compact, alignment = ReaderAlignment.Left)
      )
    assertEquals(ReaderMargins.Compact.value, compactLeftPrefs.pageMargins!!, 0.0001)
    assertEquals(TextAlign.LEFT, compactLeftPrefs.textAlign)
  }

  @Test
  fun `each ReaderFont resolves to a registered family`() {
    // Guards against an enum addition without the corresponding readiumFamily mapping update.
    val families =
      ReaderFont.entries.map { EpubTypography.toEpubPreferences(ReaderPreferences(font = it)) }
    assertEquals(ReaderFont.entries.size, families.distinctBy { it.fontFamily }.size)
  }

  @Test
  fun `reader preferences map spacing options to epub preferences`() {
    val defaultPrefs = EpubTypography.toEpubPreferences(ReaderPreferences())
    assertEquals(1.4, defaultPrefs.lineHeight!!, 0.0001)
    assertEquals(0.0, defaultPrefs.paragraphSpacing!!, 0.0001)
    assertEquals(0.0, defaultPrefs.wordSpacing!!, 0.0001)
    assertEquals(0.0, defaultPrefs.letterSpacing!!, 0.0001)

    val customPrefs =
      EpubTypography.toEpubPreferences(
        ReaderPreferences(
          lineSpacing = 1.6f,
          paragraphSpacing = 0.5f,
          wordSpacing = 0.2f,
          letterSpacing = 0.1f,
        )
      )
    assertEquals(1.6, customPrefs.lineHeight!!, 0.0001)
    assertEquals(0.5, customPrefs.paragraphSpacing!!, 0.0001)
    assertEquals(0.2, customPrefs.wordSpacing!!, 0.0001)
    assertEquals(0.1, customPrefs.letterSpacing!!, 0.0001)
  }
}
