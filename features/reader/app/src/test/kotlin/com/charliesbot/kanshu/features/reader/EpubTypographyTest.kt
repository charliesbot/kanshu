package com.charliesbot.kanshu.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun `defaults opt out of publisher styles and enforce single column`() {
    // These two anchor the "layout-mine" lane from docs/KINDLE_TYPOGRAPHY.md — flipping either
    // would silently un-do the Opinionated Normalization.
    assertFalse(EpubTypography.defaults.publisherStyles!!)
    assertEquals(ColumnCount.ONE, EpubTypography.defaults.columnCount)
  }

  @Test
  fun `initial preferences seed the bundled serif font`() {
    assertEquals(EpubTypography.notoSerif, EpubTypography.initialPreferences.fontFamily)
  }
}
