package com.charliesbot.kanshu.features.reader

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
  fun `initial preferences seed the bundled serif font`() {
    assertEquals(EpubTypography.notoSerif, EpubTypography.initialPreferences.fontFamily)
  }
}
