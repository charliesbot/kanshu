package com.charliesbot.kanshu.navigator.engine

import android.graphics.Typeface
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.model.HorizontalRule
import com.charliesbot.kanshu.navigator.model.ImageBlock
import com.charliesbot.kanshu.navigator.model.ListBlock
import com.charliesbot.kanshu.navigator.model.ListItem
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.QuoteBlock
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
  fun layout_imageBlock_producesFixedPlaceholderEntry() {
    val document =
      ReaderDocument(blocks = listOf(ImageBlock(resourceHref = "images/map.png", alt = "Map")))
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 400, heightPx = 600, density = 2f)
    val horizontalMarginPx = styleResolver.horizontalMarginPx()

    val pages =
      ReaderLayoutEngine()
        .layout(
          document = document,
          viewport = viewport,
          horizontalMarginPx = horizontalMarginPx,
          verticalMarginPx = styleResolver.verticalMarginPx(),
          justify = false,
          styleResolver = styleResolver::resolve,
        )

    val entry = pages.single().entries.single() as PageEntry.Image
    assertEquals(0, entry.blockIndex)
    assertEquals("images/map.png", entry.resourceHref)
    assertEquals("Map", entry.alt)
    assertEquals(viewport.widthPx - horizontalMarginPx * 2, entry.widthPx, 0.01f)
    assertTrue(entry.visibleHeightPx > 0f)
  }

  @Test
  fun layout_imageWithBounds_scalesDownToFitContentWidth() {
    val document =
      ReaderDocument(blocks = listOf(ImageBlock(resourceHref = "images/map.png", alt = "Map")))
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 400, heightPx = 600, density = 2f)
    val horizontalMarginPx = styleResolver.horizontalMarginPx()
    val contentWidthPx = viewport.widthPx - horizontalMarginPx * 2

    val pages =
      ReaderLayoutEngine()
        .layout(
          document = document,
          viewport = viewport,
          horizontalMarginPx = horizontalMarginPx,
          verticalMarginPx = styleResolver.verticalMarginPx(),
          justify = false,
          styleResolver = styleResolver::resolve,
          imageBounds = { ImageBounds(intrinsicWidthPx = 2000, intrinsicHeightPx = 1000) },
        )

    val entry = pages.single().entries.single() as PageEntry.Image
    assertEquals(contentWidthPx, entry.widthPx, 0.01f)
    assertEquals(contentWidthPx / 2f, entry.visibleHeightPx, 0.01f)
  }

  @Test
  fun layout_smallImageWithBounds_keepsIntrinsicSizeCentered() {
    val document =
      ReaderDocument(blocks = listOf(ImageBlock(resourceHref = "images/dot.png", alt = null)))
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 400, heightPx = 600, density = 2f)
    val horizontalMarginPx = styleResolver.horizontalMarginPx()
    val contentWidthPx = viewport.widthPx - horizontalMarginPx * 2

    val pages =
      ReaderLayoutEngine()
        .layout(
          document = document,
          viewport = viewport,
          horizontalMarginPx = horizontalMarginPx,
          verticalMarginPx = styleResolver.verticalMarginPx(),
          justify = false,
          styleResolver = styleResolver::resolve,
          imageBounds = { ImageBounds(intrinsicWidthPx = 100, intrinsicHeightPx = 40) },
        )

    val entry = pages.single().entries.single() as PageEntry.Image
    assertEquals(100f, entry.widthPx, 0.01f)
    assertEquals(40f, entry.visibleHeightPx, 0.01f)
    assertEquals((contentWidthPx - 100f) / 2f, entry.drawOffsetXPx, 0.01f)
  }

  @Test
  fun layout_tallImageWithBounds_capsHeightToContentHeight() {
    val document =
      ReaderDocument(blocks = listOf(ImageBlock(resourceHref = "images/tall.png", alt = null)))
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 400, heightPx = 600, density = 2f)
    val verticalMarginPx = styleResolver.verticalMarginPx()
    val contentHeightPx = viewport.heightPx - verticalMarginPx * 2

    val pages =
      ReaderLayoutEngine()
        .layout(
          document = document,
          viewport = viewport,
          horizontalMarginPx = styleResolver.horizontalMarginPx(),
          verticalMarginPx = verticalMarginPx,
          justify = false,
          styleResolver = styleResolver::resolve,
          imageBounds = { ImageBounds(intrinsicWidthPx = 500, intrinsicHeightPx = 5000) },
        )

    val entry = pages.single().entries.single() as PageEntry.Image
    assertEquals(contentHeightPx, entry.visibleHeightPx, 0.01f)
    assertEquals(contentHeightPx * (500f / 5000f), entry.widthPx, 0.01f)
  }

  @Test
  fun layout_invalidImageBounds_fallsBackToPlaceholderEntry() {
    val document =
      ReaderDocument(blocks = listOf(ImageBlock(resourceHref = "images/broken.png", alt = null)))
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 400, heightPx = 600, density = 2f)

    fun layoutWith(bounds: (String) -> ImageBounds?): PageEntry.Image =
      ReaderLayoutEngine()
        .layout(
          document = document,
          viewport = viewport,
          horizontalMarginPx = styleResolver.horizontalMarginPx(),
          verticalMarginPx = styleResolver.verticalMarginPx(),
          justify = false,
          styleResolver = styleResolver::resolve,
          imageBounds = bounds,
        )
        .single()
        .entries
        .single() as PageEntry.Image

    val placeholder = layoutWith { null }
    val zeroWidth = layoutWith { ImageBounds(intrinsicWidthPx = 0, intrinsicHeightPx = 100) }

    assertEquals(placeholder, zeroWidth)
  }

  @Test
  fun layout_blockQuote_indentsTextAndCarriesLeadingRule() {
    val document =
      ReaderDocument(
        blocks =
          listOf(
            QuoteBlock(
              listOf(ParagraphBlock(listOf(TextLeaf("Quoted paragraph with enough words."))))
            )
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

    val entry = pages.single().entries.single() as PageEntry.FullBlock
    assertTrue(entry.drawOffsetXPx > 0f)
    assertTrue(entry.leadingRuleStrokeWidthPx > 0f)
    assertTrue(entry.leadingRuleOffsetXPx < entry.drawOffsetXPx)
  }

  @Test
  fun layout_listBlock_indentsItemsAndCarriesMarkers() {
    val document =
      ReaderDocument(
        blocks =
          listOf(
            ListBlock(
              ordered = true,
              items =
                listOf(
                  ListItem(listOf(ParagraphBlock(listOf(TextLeaf("First item"))))),
                  ListItem(listOf(ParagraphBlock(listOf(TextLeaf("Second item"))))),
                ),
            )
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
    assertEquals(2, entries.size)
    val firstEntry = entries[0] as PageEntry.FullBlock
    val secondEntry = entries[1] as PageEntry.FullBlock
    assertEquals("1.", firstEntry.markerText)
    assertEquals("2.", secondEntry.markerText)
    assertTrue(firstEntry.markerOffsetXPx < firstEntry.drawOffsetXPx)
    assertTrue(firstEntry.drawOffsetXPx > 0f)
  }

  @Test
  fun layout_blockQuoteWithList_keepsListTextVisible() {
    val document =
      ReaderDocument(
        blocks =
          listOf(
            QuoteBlock(
              listOf(
                ListBlock(
                  ordered = false,
                  items = listOf(ListItem(listOf(ParagraphBlock(listOf(TextLeaf("Quoted item")))))),
                )
              )
            )
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

    assertTrue(pages.single().entries.single() is PageEntry.FullBlock)
  }

  @Test
  fun layout_nestedList_emitsIndentedChildEntriesWithoutBlankGap() {
    val document =
      ReaderDocument(
        blocks =
          listOf(
            ListBlock(
              ordered = true,
              items =
                listOf(
                  ListItem(
                    listOf(
                      ParagraphBlock(listOf(TextLeaf("PART ONE: CONSPIRITUALITY 101"))),
                      ListBlock(
                        ordered = true,
                        items =
                          listOf(
                            ListItem(listOf(ParagraphBlock(listOf(TextLeaf("Charlotte's Web"))))),
                            ListItem(
                              listOf(
                                ParagraphBlock(listOf(TextLeaf("The Mystic and Paranoid Trifecta")))
                              )
                            ),
                          ),
                      ),
                    )
                  ),
                  ListItem(
                    listOf(ParagraphBlock(listOf(TextLeaf("PART TWO: STRANGE ATTRACTORS"))))
                  ),
                ),
            )
          )
      )
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 700, heightPx = 900, density = 2f)

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

    val entries = pages.single().entries.map { it as PageEntry.FullBlock }
    assertEquals(4, entries.size)
    assertEquals("1.", entries[0].markerText)
    assertEquals("1.", entries[1].markerText)
    assertEquals("2.", entries[2].markerText)
    assertEquals("2.", entries[3].markerText)
    assertTrue(entries[1].drawOffsetXPx > entries[0].drawOffsetXPx)
    assertTrue(entries[1].markerOffsetXPx > entries[0].markerOffsetXPx)
    assertTrue(
      "nested list should not start after a blank paragraph-sized gap",
      entries[1].yOffsetPx - entries[0].yOffsetPx <= entries[0].visibleHeightPx * 1.25f,
    )
  }

  @Test
  fun layout_nestedList_preservesListItemBlockOrder() {
    val document =
      ReaderDocument(
        blocks =
          listOf(
            ListBlock(
              ordered = true,
              items =
                listOf(
                  ListItem(
                    listOf(
                      ParagraphBlock(listOf(TextLeaf("Before nested list"))),
                      ListBlock(
                        ordered = true,
                        items =
                          listOf(ListItem(listOf(ParagraphBlock(listOf(TextLeaf("Nested item")))))),
                      ),
                      ParagraphBlock(listOf(TextLeaf("After nested list"))),
                    )
                  )
                ),
            )
          )
      )
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 700, heightPx = 900, density = 2f)

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

    val entries = pages.single().entries.map { it as PageEntry.FullBlock }
    assertEquals(3, entries.size)
    assertEquals("Before nested list", entries[0].layout.text.toString())
    assertEquals("Nested item", entries[1].layout.text.toString())
    assertEquals("After nested list", entries[2].layout.text.toString())
    assertEquals("1.", entries[0].markerText)
    assertEquals("1.", entries[1].markerText)
    assertEquals(null, entries[2].markerText)
    assertEquals(entries[0].drawOffsetXPx, entries[2].drawOffsetXPx, 0.01f)
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
