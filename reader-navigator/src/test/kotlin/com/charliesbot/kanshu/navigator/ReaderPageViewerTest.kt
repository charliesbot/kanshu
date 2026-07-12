package com.charliesbot.kanshu.navigator

import com.charliesbot.kanshu.navigator.engine.PageEntry
import com.charliesbot.kanshu.navigator.engine.ReaderPage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageViewerTest {
  @Test
  fun pendingImageEntries_returnsOnlyUndecidedImageEntries() {
    val undecided =
      PageEntry.Image(
        blockIndex = 0,
        yOffsetPx = 0f,
        visibleHeightPx = 10f,
        drawOffsetXPx = 0f,
        resourceHref = "images/a.png",
        alt = null,
        widthPx = 10f,
      )
    val decided = undecided.copy(blockIndex = 1, resourceHref = "images/b.png")
    val blankHref = undecided.copy(blockIndex = 2, resourceHref = "")
    val page = ReaderPage(entries = listOf(undecided, decided, blankHref))

    assertEquals(listOf(undecided), page.pendingImageEntries(decidedHrefs = setOf("images/b.png")))
  }

  @Test
  fun pendingImageEntries_deduplicatesRepeatedHrefs() {
    val first =
      PageEntry.Image(
        blockIndex = 0,
        yOffsetPx = 0f,
        visibleHeightPx = 10f,
        drawOffsetXPx = 0f,
        resourceHref = "images/a.png",
        alt = null,
        widthPx = 10f,
      )
    val repeat = first.copy(blockIndex = 1)
    val page = ReaderPage(entries = listOf(first, repeat))

    assertEquals(listOf(first), page.pendingImageEntries(decidedHrefs = emptySet()))
  }

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
