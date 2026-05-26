package com.charliesbot.kanshu.navigator.engine

import android.graphics.Typeface
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.model.TextLeaf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ReaderLayoutEngineTest {
  @Test
  fun layout_singleParagraph_producesRenderablePage() {
    val document =
      ReaderDocument(blocks = listOf(ParagraphBlock(listOf(TextLeaf("Hello reader.")))))
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 400, heightPx = 600, density = 2f)

    val pages =
      ReaderLayoutEngine()
        .layout(
          document = document,
          viewport = viewport,
          horizontalMarginPx = styleResolver.horizontalMarginPx(),
          verticalMarginPx = styleResolver.verticalMarginPx(),
          justify = false,
          styleResolver = styleResolver::resolve,
        )

    assertEquals(1, pages.size)
    assertTrue(pages.first().entries.isNotEmpty())
    assertTrue(pages.first().entries.all { it is PageEntry.FullBlock })
  }

  @Test
  fun layout_multipleParagraphs_paginatesAcrossPages() {
    val document =
      ReaderDocument(
        blocks =
          List(8) { index ->
            ParagraphBlock(listOf(TextLeaf("Paragraph number $index with enough words to wrap.")))
          }
      )
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 400, heightPx = 200, density = 2f)

    val pages =
      ReaderLayoutEngine()
        .layout(
          document = document,
          viewport = viewport,
          horizontalMarginPx = styleResolver.horizontalMarginPx(),
          verticalMarginPx = styleResolver.verticalMarginPx(),
          justify = false,
          styleResolver = styleResolver::resolve,
        )

    assertTrue(pages.size > 1)
    assertEquals(8, pages.sumOf { page -> page.entries.map { it.blockIndex }.distinct().size })
  }

  @Test
  fun layout_blankParagraph_isNotPaginated() {
    val document =
      ReaderDocument(
        blocks =
          List(18) { index ->
            ParagraphBlock(listOf(TextLeaf("Paragraph number $index with enough words to wrap.")))
          } + ParagraphBlock(listOf(TextLeaf("   ")))
      )
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 632, heightPx = 840, density = 2f)

    val pages =
      ReaderLayoutEngine()
        .layout(
          document = document,
          viewport = viewport,
          horizontalMarginPx = styleResolver.horizontalMarginPx(),
          verticalMarginPx = styleResolver.verticalMarginPx(),
          justify = false,
          styleResolver = styleResolver::resolve,
        )

    assertEquals(1, pages.size)
  }

  @Test
  fun layout_trailingOrphanLine_staysOnPreviousPage() {
    val blocks =
      List(18) { index ->
        ParagraphBlock(
          listOf(TextLeaf("Paragraph number $index with enough words to wrap across lines."))
        )
      } + ParagraphBlock(listOf(TextLeaf("E3-20230509-JV-PC-REV")))
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 632, heightPx = 840, density = 2f)

    val pages =
      ReaderLayoutEngine()
        .layout(
          document = ReaderDocument(blocks = blocks),
          viewport = viewport,
          horizontalMarginPx = styleResolver.horizontalMarginPx(),
          verticalMarginPx = styleResolver.verticalMarginPx(),
          justify = false,
          styleResolver = styleResolver::resolve,
        )

    assertEquals(1, pages.size)
    assertEquals(19, pages.single().entries.map { it.blockIndex }.distinct().size)
  }
}
