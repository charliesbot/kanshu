package com.charliesbot.kanshu.navigator.engine

import android.graphics.Typeface
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.model.HorizontalRule
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
  fun layout_headingAndHorizontalRule_producesRenderableEntries() {
    val document =
      ReaderDocument(
        blocks =
          listOf(
            HeadingBlock(level = 1, spans = listOf(TextLeaf("Chapter One"))),
            HorizontalRule,
            ParagraphBlock(listOf(TextLeaf("Opening paragraph."))),
          )
      )
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

    val entries = pages.single().entries
    assertEquals(3, entries.size)
    assertTrue(entries[0] is PageEntry.FullBlock)
    assertTrue(entries[1] is PageEntry.HorizontalRule)
    assertTrue(entries[2] is PageEntry.FullBlock)
  }

  @Test
  fun layout_overflowingTrailingLine_movesToNextPage() {
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

    assertEquals(2, pages.size)
    assertEquals(18, pages.first().entries.map { it.blockIndex }.distinct().size)
    assertEquals(18, pages.last().entries.single().blockIndex)
  }

  @Test
  fun layout_entriesStayWithinContentHeight() {
    val blocks =
      List(18) { index ->
        ParagraphBlock(listOf(TextLeaf("Copyright paragraph $index. ${"text ".repeat(25)}")))
      } + ParagraphBlock(listOf(TextLeaf("E3-20230509-JV-PC-REV")))
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 632, heightPx = 840, density = 2f)
    val verticalMarginPx = styleResolver.verticalMarginPx()
    val contentHeightPx = viewport.heightPx - verticalMarginPx * 2

    val pages =
      ReaderLayoutEngine()
        .layout(
          document = ReaderDocument(blocks = blocks),
          viewport = viewport,
          horizontalMarginPx = styleResolver.horizontalMarginPx(),
          verticalMarginPx = verticalMarginPx,
          justify = false,
          styleResolver = styleResolver::resolve,
        )

    pages.forEachIndexed { pageIndex, page ->
      page.entries.forEach { entry ->
        assertTrue(
          "page $pageIndex entry ${entry.blockIndex} exceeds content height",
          entry.yOffsetPx + entry.visibleHeightPx <= contentHeightPx,
        )
      }
    }
  }

  @Test
  fun layout_overflowingParagraphUsesRemainingPageSpace() {
    val leadingBlocks =
      List(5) { index ->
        ParagraphBlock(listOf(TextLeaf("Lead paragraph $index with enough words to wrap once.")))
      }
    val overflowingBlock =
      ParagraphBlock(listOf(TextLeaf(List(80) { "overflow line $it" }.joinToString("\n"))))
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 400, heightPx = 500, density = 2f)

    val pages =
      ReaderLayoutEngine()
        .layout(
          document = ReaderDocument(blocks = leadingBlocks + overflowingBlock),
          viewport = viewport,
          horizontalMarginPx = styleResolver.horizontalMarginPx(),
          verticalMarginPx = styleResolver.verticalMarginPx(),
          justify = false,
          styleResolver = styleResolver::resolve,
        )

    assertTrue(
      "first page should use remaining space for the next paragraph",
      pages.first().entries.any { entry ->
        entry is PageEntry.SplitBlock && entry.blockIndex == leadingBlocks.size
      },
    )
  }
}
