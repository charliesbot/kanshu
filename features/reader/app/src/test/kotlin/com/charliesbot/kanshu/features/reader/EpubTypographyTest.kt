package com.charliesbot.kanshu.features.reader

import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.navigator.preferences.ColumnCount
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
    assertEquals(EpubTypography.notoSerif, prefs.fontFamily)
    assertEquals(1.0, prefs.fontSize!!, 0.0001)
  }

  @Test
  fun `each ReaderFont resolves to a registered family`() {
    // Guards against an enum addition without the corresponding readiumFamily mapping update.
    val families =
      ReaderFont.entries.map { EpubTypography.toEpubPreferences(ReaderPreferences(font = it)) }
    assertEquals(ReaderFont.entries.size, families.distinctBy { it.fontFamily }.size)
  }
}
