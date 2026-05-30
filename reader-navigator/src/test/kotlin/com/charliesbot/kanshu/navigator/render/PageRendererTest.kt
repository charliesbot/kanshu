package com.charliesbot.kanshu.navigator.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.navigator.engine.BlockStyleResolver
import com.charliesbot.kanshu.navigator.engine.PageEntry
import com.charliesbot.kanshu.navigator.engine.ReaderLayoutEngine
import com.charliesbot.kanshu.navigator.engine.ReaderViewport
import com.charliesbot.kanshu.navigator.model.HorizontalRule
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
class PageRendererTest {
  @Test
  fun draw_secondPageAfterSplit_rendersVisibleText() {
    val shortParagraphs =
      List(17) { index -> ParagraphBlock(listOf(TextLeaf("Short paragraph number $index."))) }
    val longParagraph = ParagraphBlock(listOf(TextLeaf("word ".repeat(250))))
    val document = ReaderDocument(blocks = shortParagraphs + longParagraph)

    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 400, heightPx = 500, density = 2f)
    val horizontalMarginPx = styleResolver.horizontalMarginPx()
    val verticalMarginPx = styleResolver.verticalMarginPx()

    val pages =
      ReaderLayoutEngine()
        .layout(
          document = document,
          viewport = viewport,
          horizontalMarginPx = horizontalMarginPx,
          verticalMarginPx = verticalMarginPx,
          justify = false,
          styleResolver = styleResolver::resolve,
        )

    assertTrue(pages.size >= 2)
    val secondPage = pages[1]
    assertTrue(secondPage.entries.isNotEmpty())
    assertTrue(
      secondPage.entries.any { entry ->
        entry is PageEntry.SplitBlock || entry is PageEntry.FullBlock
      }
    )

    val bitmap = Bitmap.createBitmap(viewport.widthPx, viewport.heightPx, Bitmap.Config.ARGB_8888)
    PageRenderer.draw(
      canvas = Canvas(bitmap),
      page = secondPage,
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
    )

    assertTrue(hasNonBackgroundPixel(bitmap))
  }

  @Test
  fun draw_overflowingTrailingLine_rendersOnSecondPage() {
    val blocks =
      List(18) { index ->
        ParagraphBlock(listOf(TextLeaf("Copyright paragraph $index. ${"text ".repeat(25)}")))
      } + ParagraphBlock(listOf(TextLeaf("E3-20230509-JV-PC-REV")))
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 632, heightPx = 840, density = 2f)
    val horizontalMarginPx = styleResolver.horizontalMarginPx()
    val verticalMarginPx = styleResolver.verticalMarginPx()

    val pages =
      ReaderLayoutEngine()
        .layout(
          document = ReaderDocument(blocks = blocks),
          viewport = viewport,
          horizontalMarginPx = horizontalMarginPx,
          verticalMarginPx = verticalMarginPx,
          justify = false,
          styleResolver = styleResolver::resolve,
        )

    assertEquals(2, pages.size)

    val bitmap = Bitmap.createBitmap(viewport.widthPx, viewport.heightPx, Bitmap.Config.ARGB_8888)
    PageRenderer.draw(
      canvas = Canvas(bitmap),
      page = pages.last(),
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
    )

    assertTrue(hasNonBackgroundPixel(bitmap))
  }

  @Test
  fun draw_horizontalRule_rendersLine() {
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 400, heightPx = 300, density = 2f)
    val horizontalMarginPx = styleResolver.horizontalMarginPx()
    val verticalMarginPx = styleResolver.verticalMarginPx()

    val pages =
      ReaderLayoutEngine()
        .layout(
          document = ReaderDocument(blocks = listOf(HorizontalRule)),
          viewport = viewport,
          horizontalMarginPx = horizontalMarginPx,
          verticalMarginPx = verticalMarginPx,
          justify = false,
          styleResolver = styleResolver::resolve,
        )

    assertTrue(pages.single().entries.single() is PageEntry.HorizontalRule)

    val bitmap = Bitmap.createBitmap(viewport.widthPx, viewport.heightPx, Bitmap.Config.ARGB_8888)
    PageRenderer.draw(
      canvas = Canvas(bitmap),
      page = pages.single(),
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
    )

    assertTrue(hasNonBackgroundPixel(bitmap))
  }

  @Test
  fun draw_blockQuote_rendersLeadingRuleInGutter() {
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val viewport = ReaderViewport(widthPx = 400, heightPx = 300, density = 2f)
    val horizontalMarginPx = styleResolver.horizontalMarginPx()
    val verticalMarginPx = styleResolver.verticalMarginPx()

    val pages =
      ReaderLayoutEngine()
        .layout(
          document =
            ReaderDocument(
              blocks =
                listOf(
                  QuoteBlock(
                    listOf(ParagraphBlock(listOf(TextLeaf("Quoted paragraph for rendering."))))
                  )
                )
            ),
          viewport = viewport,
          horizontalMarginPx = horizontalMarginPx,
          verticalMarginPx = verticalMarginPx,
          justify = false,
          styleResolver = styleResolver::resolve,
        )

    val entry = pages.single().entries.single() as PageEntry.FullBlock
    val bitmap = Bitmap.createBitmap(viewport.widthPx, viewport.heightPx, Bitmap.Config.ARGB_8888)
    PageRenderer.draw(
      canvas = Canvas(bitmap),
      page = pages.single(),
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
    )

    val ruleX = (horizontalMarginPx + entry.leadingRuleOffsetXPx).toInt()
    assertTrue(hasNonBackgroundPixelNearX(bitmap, ruleX))
  }

  private fun hasNonBackgroundPixel(bitmap: Bitmap): Boolean {
    for (x in 0 until bitmap.width step 8) {
      for (y in 0 until bitmap.height step 8) {
        if (bitmap.getPixel(x, y) != Color.WHITE) return true
      }
    }
    return false
  }

  private fun hasNonBackgroundPixelNearX(bitmap: Bitmap, x: Int): Boolean {
    val fromX = (x - 2).coerceAtLeast(0)
    val toX = (x + 2).coerceAtMost(bitmap.width - 1)
    for (scanX in fromX..toX) {
      for (y in 0 until bitmap.height step 4) {
        if (bitmap.getPixel(scanX, y) != Color.WHITE) return true
      }
    }
    return false
  }
}
