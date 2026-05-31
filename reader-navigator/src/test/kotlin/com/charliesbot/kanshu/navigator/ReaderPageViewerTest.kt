package com.charliesbot.kanshu.navigator

import com.charliesbot.kanshu.navigator.engine.ReaderPage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageViewerTest {
  @Test
  fun hasRenderablePage_emptyList_isNotRenderable() {
    assertFalse(emptyList<ReaderPage>().hasRenderablePage())
  }

  @Test
  fun hasRenderablePage_blankPage_isRenderable() {
    assertTrue(listOf(ReaderPage(emptyList())).hasRenderablePage())
  }

  @Test
  fun toSelectionLocale_usesDocumentLanguageTag() {
    assertEquals(Locale.forLanguageTag("es"), "es".toSelectionLocale())
  }

  @Test
  fun toSelectionLocale_blankLanguageFallsBackToDefaultLocale() {
    assertEquals(Locale.getDefault(), "".toSelectionLocale())
  }
}
