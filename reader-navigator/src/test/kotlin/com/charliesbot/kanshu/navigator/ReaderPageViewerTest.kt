package com.charliesbot.kanshu.navigator

import android.graphics.RectF
import com.charliesbot.kanshu.navigator.engine.ReaderPage
import com.charliesbot.kanshu.navigator.render.SelectionPageTurnDirection
import com.charliesbot.kanshu.navigator.selection.TextSelection
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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

  @Test
  fun turnSelectionPage_previousAfterForwardRestoresPriorPageSelection() {
    val originalSelection = textSelection("middle to end", range = 4..12)
    val nextPageSelection = textSelection("next page")
    val afterForward =
      SelectionCarryState()
        .turnSelectionPage(
          direction = SelectionPageTurnDirection.Next,
          currentPage = 0,
          lastPageIndex = 2,
          pageSelectedText = originalSelection.text,
          currentSelection = originalSelection,
        )

    requireNotNull(afterForward)
    val afterPrevious =
      afterForward.carryState.turnSelectionPage(
        direction = SelectionPageTurnDirection.Previous,
        currentPage = 1,
        lastPageIndex = 2,
        pageSelectedText = nextPageSelection.text,
        currentSelection = nextPageSelection,
      )

    requireNotNull(afterPrevious)
    assertEquals(0, afterPrevious.targetPage)
    assertEquals(false, afterPrevious.seedAtPageEnd)
    assertSame(originalSelection, afterPrevious.restoredSelection)
    assertEquals(emptyList<String>(), afterPrevious.carryState.prefixPages)
    assertEquals(emptyList<String>(), afterPrevious.carryState.suffixPages)
  }

  @Test
  fun turnSelectionPage_previousAfterForwardSavesCurrentPageSelectionForRestore() {
    val firstPageSelection = textSelection("first page")
    val latestSecondPageSelection = textSelection("second page latest")
    val afterForward =
      SelectionCarryState()
        .turnSelectionPage(
          direction = SelectionPageTurnDirection.Next,
          currentPage = 0,
          lastPageIndex = 2,
          pageSelectedText = firstPageSelection.text,
          currentSelection = firstPageSelection,
        )

    requireNotNull(afterForward)
    val afterPrevious =
      afterForward.carryState.turnSelectionPage(
        direction = SelectionPageTurnDirection.Previous,
        currentPage = 1,
        lastPageIndex = 2,
        pageSelectedText = latestSecondPageSelection.text,
        currentSelection = latestSecondPageSelection,
      )

    requireNotNull(afterPrevious)
    assertSame(latestSecondPageSelection, afterPrevious.carryState.pageSelections[1])
  }

  @Test
  fun turnSelectionPage_previousWithoutPrefixSeedsPreviousPageEndAndKeepsSuffix() {
    val currentSelection = textSelection("current page")

    val turn =
      SelectionCarryState()
        .turnSelectionPage(
          direction = SelectionPageTurnDirection.Previous,
          currentPage = 1,
          lastPageIndex = 2,
          pageSelectedText = currentSelection.text,
          currentSelection = currentSelection,
        )

    requireNotNull(turn)
    assertEquals(0, turn.targetPage)
    assertEquals(true, turn.seedAtPageEnd)
    assertEquals(null, turn.restoredSelection)
    assertEquals(listOf("current page"), turn.carryState.suffixPages)
    assertNotNull(turn.carryState.pageSelections[1])
  }

  private fun textSelection(text: String, range: IntRange = 0 until text.length): TextSelection =
    TextSelection(
      blockIndex = 0,
      entryIndex = 0,
      anchorRange = range,
      range = range,
      text = text,
      rects = listOf(RectF(0f, 0f, 1f, 1f)),
      anchor = RectF(0f, 0f, 1f, 1f),
    )
}
