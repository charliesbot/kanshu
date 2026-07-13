package com.charliesbot.kanshu.navigator.selection

import android.graphics.Typeface
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.navigator.engine.BlockStyleResolver
import com.charliesbot.kanshu.navigator.engine.PageEntry
import com.charliesbot.kanshu.navigator.engine.ReaderLayoutEngine
import com.charliesbot.kanshu.navigator.engine.ReaderViewport
import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.LinkSpan
import com.charliesbot.kanshu.navigator.model.ListBlock
import com.charliesbot.kanshu.navigator.model.ListItem
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.ReaderBlock
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.model.TextLeaf
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ReaderSelectionTest {
  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun linkHrefAt_returnsHrefOverLinkTextAndNullElsewhere() {
    val document =
      ReaderDocument(
        blocks =
          listOf(
            ParagraphBlock(
              listOf(TextLeaf("See "), LinkSpan("note.xhtml", listOf(TextLeaf("the note"))))
            )
          )
      )
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, density = 2f)
    val horizontalMarginPx = styleResolver.horizontalMarginPx()
    val verticalMarginPx = styleResolver.verticalMarginPx()
    val page =
      ReaderLayoutEngine()
        .layout(
          document = document,
          viewport = ReaderViewport(widthPx = 600, heightPx = 600, density = 2f),
          horizontalMarginPx = horizontalMarginPx,
          verticalMarginPx = verticalMarginPx,
          justify = false,
          styleResolver = styleResolver::resolve,
        )
        .single()
    val entry = page.entries.single() as PageEntry.FullBlock
    val layout = entry.layout
    val lineCenterY =
      verticalMarginPx + entry.yOffsetPx + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f
    val insideLinkX =
      horizontalMarginPx + entry.drawOffsetXPx + layout.getPrimaryHorizontal("See t".length) + 1f
    val beforeLinkX = horizontalMarginPx + entry.drawOffsetXPx + layout.getPrimaryHorizontal(1) + 1f

    assertEquals(
      "note.xhtml",
      ReaderSelector.linkHrefAt(
        page,
        insideLinkX,
        lineCenterY,
        horizontalMarginPx,
        verticalMarginPx,
      ),
    )
    assertNull(
      ReaderSelector.linkHrefAt(
        page,
        beforeLinkX,
        lineCenterY,
        horizontalMarginPx,
        verticalMarginPx,
      )
    )
  }

  @Test
  fun selectWordAt_hitsExpectedWordInFullBlock() {
    val fixture = layoutDocument("Alpha beta gamma.")
    val entry = fixture.page.entries.single() as PageEntry.FullBlock
    val alphaOffset = entry.layout.text.indexOf("Alpha")
    val touchX =
      fixture.horizontalMarginPx +
        entry.drawOffsetXPx +
        entry.layout.paint.measureText(entry.layout.text, 0, alphaOffset + 2)
    val touchY =
      fixture.verticalMarginPx +
        entry.yOffsetPx +
        (entry.layout.getLineTop(0) + entry.layout.getLineBottom(0)) / 2f

    val selection =
      ReaderSelector.selectWordAt(
        page = fixture.page,
        xPx = touchX,
        yPx = touchY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(selection)
    assertEquals(0, selection.blockIndex)
    assertEquals(alphaOffset until alphaOffset + "Alpha".length, selection.range)
    assertEquals("Alpha", selection.text)
    assertFalse(selection.rects.isEmpty())
    assertEquals(
      fixture.horizontalMarginPx +
        entry.drawOffsetXPx +
        entry.layout.paint.measureText(entry.layout.text, 0, selection.range.first),
      selection.rects.first().left,
      0.01f,
    )
    assertEquals(
      fixture.verticalMarginPx + entry.yOffsetPx + entry.layout.getLineTop(0),
      selection.rects.first().top,
      0.01f,
    )
    assertTrue(selection.anchor.width() > 0f)
    assertTrue(selection.anchor.height() > 0f)
  }

  @Test
  fun selectWordAt_accountsForSplitBlockFirstLineOffset() {
    val blocks =
      listOf(ParagraphBlock(listOf(TextLeaf(List(80) { "target line $it" }.joinToString("\n")))))
    val fixture =
      layoutDocument(
        blocks,
        viewport = ReaderViewport(400, 300, density = 2f),
        pagePicker = { pages ->
          pages.first { page -> page.entries.any { it is PageEntry.SplitBlock } }
        },
      )
    val splitEntry =
      fixture.page.entries.first { entry -> entry is PageEntry.SplitBlock } as PageEntry.SplitBlock
    val visibleLine = splitEntry.lineRange.first
    val lineStart = splitEntry.layout.getLineStart(visibleLine)
    val touchX =
      fixture.horizontalMarginPx +
        splitEntry.drawOffsetXPx +
        splitEntry.layout.paint.measureText(
          splitEntry.layout.text,
          splitEntry.layout.getLineStart(visibleLine),
          lineStart + 2,
        )
    val touchY =
      fixture.verticalMarginPx + splitEntry.yOffsetPx + splitEntry.layout.getLineTop(visibleLine) -
        splitEntry.firstLineTopPx + 1f

    val selection =
      ReaderSelector.selectWordAt(
        page = fixture.page,
        xPx = touchX,
        yPx = touchY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(selection)
    assertEquals(splitEntry.blockIndex, selection.blockIndex)
    assertEquals("target", selection.text)
    assertTrue(selection.range.first >= splitEntry.layout.getLineStart(splitEntry.lineRange.first))
    assertTrue(selection.range.last <= splitEntry.layout.getLineEnd(splitEntry.lineRange.last))
    assertEquals(
      fixture.horizontalMarginPx +
        splitEntry.drawOffsetXPx +
        splitEntry.layout.paint.measureText(
          splitEntry.layout.text,
          splitEntry.layout.getLineStart(visibleLine),
          selection.range.first,
        ),
      selection.rects.first().left,
      0.01f,
    )
    assertEquals(
      fixture.verticalMarginPx + splitEntry.yOffsetPx + splitEntry.layout.getLineTop(visibleLine) -
        splitEntry.firstLineTopPx,
      selection.rects.first().top,
      0.01f,
    )
  }

  @Test
  fun selectWordAt_ignoresTouchesBeforeTextColumn() {
    val fixture = layoutDocument("Alpha beta gamma.")
    val entry = fixture.page.entries.single() as PageEntry.FullBlock
    val touchX = fixture.horizontalMarginPx + entry.drawOffsetXPx - 4f
    val touchY =
      fixture.verticalMarginPx +
        entry.yOffsetPx +
        (entry.layout.getLineTop(0) + entry.layout.getLineBottom(0)) / 2f

    val selection =
      ReaderSelector.selectWordAt(
        page = fixture.page,
        xPx = touchX,
        yPx = touchY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    assertEquals(null, selection)
  }

  @Test
  fun selectWordAt_ignoresTouchesBetweenWords() {
    val fixture = layoutDocument("Alpha beta gamma.")
    val entry = fixture.page.entries.single() as PageEntry.FullBlock
    val alphaEndOffset = entry.layout.text.indexOf(" ")
    val betaStartOffset = entry.layout.text.indexOf("beta")
    val alphaRight = entry.layout.paint.measureText(entry.layout.text, 0, alphaEndOffset)
    val betaLeft = entry.layout.paint.measureText(entry.layout.text, 0, betaStartOffset)
    val touchX = fixture.horizontalMarginPx + entry.drawOffsetXPx + (alphaRight + betaLeft) / 2f
    val touchY =
      fixture.verticalMarginPx +
        entry.yOffsetPx +
        (entry.layout.getLineTop(0) + entry.layout.getLineBottom(0)) / 2f

    val selection =
      ReaderSelector.selectWordAt(
        page = fixture.page,
        xPx = touchX,
        yPx = touchY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    assertEquals(null, selection)
  }

  @Test
  fun selectWordAt_ignoresTouchesAfterLineText() {
    val fixture = layoutDocument("Alpha")
    val entry = fixture.page.entries.single() as PageEntry.FullBlock
    val touchX =
      fixture.horizontalMarginPx +
        entry.drawOffsetXPx +
        entry.layout.paint.measureText(entry.layout.text, 0, entry.layout.text.length) +
        24f
    val touchY =
      fixture.verticalMarginPx +
        entry.yOffsetPx +
        (entry.layout.getLineTop(0) + entry.layout.getLineBottom(0)) / 2f

    val selection =
      ReaderSelector.selectWordAt(
        page = fixture.page,
        xPx = touchX,
        yPx = touchY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    assertEquals(null, selection)
  }

  @Test
  fun selectWordAt_hitsWordAtTrailingInsertionOffset() {
    val fixture = layoutDocument("Alpha")
    val entry = fixture.page.entries.single() as PageEntry.FullBlock
    val touchX =
      fixture.horizontalMarginPx +
        entry.drawOffsetXPx +
        entry.layout.paint.measureText(entry.layout.text, 0, entry.layout.text.length)
    val touchY =
      fixture.verticalMarginPx +
        entry.yOffsetPx +
        (entry.layout.getLineTop(0) + entry.layout.getLineBottom(0)) / 2f

    val selection =
      ReaderSelector.selectWordAt(
        page = fixture.page,
        xPx = touchX,
        yPx = touchY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(selection)
    assertEquals("Alpha", selection.text)
  }

  @Test
  fun startSelectionAt_highlightsSameRangeAsText() {
    val fixture = layoutDocument("this book")
    val entry = fixture.page.entries.single() as PageEntry.FullBlock
    val bookOffset = entry.layout.text.indexOf("book")
    val touchX =
      fixture.horizontalMarginPx +
        entry.drawOffsetXPx +
        entry.layout.paint.measureText(entry.layout.text, 0, bookOffset + 2)
    val touchY =
      fixture.verticalMarginPx +
        entry.yOffsetPx +
        (entry.layout.getLineTop(0) + entry.layout.getLineBottom(0)) / 2f

    val selection =
      ReaderSelector.startSelectionAt(
        page = fixture.page,
        xPx = touchX,
        yPx = touchY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(selection)
    assertEquals("book", selection.text)
    assertEquals(bookOffset until bookOffset + "book".length, selection.range)
    assertEquals(
      fixture.horizontalMarginPx +
        entry.drawOffsetXPx +
        entry.layout.paint.measureText(entry.layout.text, 0, bookOffset),
      selection.rects.first().left,
      0.01f,
    )
  }

  @Test
  fun startSelectionAt_usesStyledSpanMetricsForWordBounds() {
    val fixture =
      layoutDocument(
        listOf(
          ParagraphBlock(
            listOf(TextLeaf("this "), TextLeaf("book", InlineStyle.Bold), TextLeaf(" now"))
          )
        )
      )
    val entry = fixture.page.entries.single() as PageEntry.FullBlock
    val bookOffset = entry.layout.text.indexOf("book")
    val touchX =
      fixture.horizontalMarginPx +
        entry.drawOffsetXPx +
        entry.layout.text.measureText(entry.layout.paint, 0, bookOffset + "book".length) - 1f
    val touchY =
      fixture.verticalMarginPx +
        entry.yOffsetPx +
        (entry.layout.getLineTop(0) + entry.layout.getLineBottom(0)) / 2f

    val selection =
      ReaderSelector.startSelectionAt(
        page = fixture.page,
        xPx = touchX,
        yPx = touchY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(selection)
    assertEquals("book", selection.text)
    assertEquals(bookOffset until bookOffset + "book".length, selection.range)
  }

  @Test
  fun startSelectionAt_nonJustifiedEntryUsesMeasuredTextBounds() {
    val fixture = layoutDocument("alpha beta gamma", justify = false)
    val entry = fixture.page.entries.single() as PageEntry.FullBlock
    val betaOffset = entry.layout.text.indexOf("beta")
    val touchY =
      fixture.verticalMarginPx +
        entry.yOffsetPx +
        (entry.layout.getLineTop(0) + entry.layout.getLineBottom(0)) / 2f

    val selection =
      ReaderSelector.startSelectionAt(
        page = fixture.page,
        xPx =
          fixture.horizontalMarginPx +
            entry.drawOffsetXPx +
            entry.layout.paint.measureText(entry.layout.text, 0, betaOffset + 2),
        yPx = touchY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(selection)
    assertFalse(entry.textJustified)
    assertEquals("beta", selection.text)
    assertEquals(
      fixture.horizontalMarginPx +
        entry.drawOffsetXPx +
        entry.layout.paint.measureText(entry.layout.text, 0, betaOffset),
      selection.rects.first().left,
      0.01f,
    )
  }

  @Test
  fun startSelectionAtPageStart_selectsFirstVisibleWord() {
    val fixture =
      layoutDocument(
        listOf(
          ParagraphBlock(listOf(TextLeaf("   "))),
          ParagraphBlock(listOf(TextLeaf("alpha beta"))),
        )
      )

    val selection =
      ReaderSelector.startSelectionAtPageStart(
        page = fixture.page,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(selection)
    assertEquals("alpha", selection.text)
    assertFalse(selection.rects.isEmpty())
  }

  @Test
  fun startSelectionAtPageEnd_selectsLastVisibleWord() {
    val fixture =
      layoutDocument(
        listOf(
          ParagraphBlock(listOf(TextLeaf("alpha beta"))),
          ParagraphBlock(listOf(TextLeaf("gamma delta"))),
        )
      )

    val selection =
      ReaderSelector.startSelectionAtPageEnd(
        page = fixture.page,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(selection)
    assertEquals("delta", selection.text)
    assertFalse(selection.rects.isEmpty())
  }

  @Test
  fun updateSelectionToPageEnd_extendsSelectionToLastVisibleWord() {
    val fixture =
      layoutDocument(
        listOf(
          ParagraphBlock(listOf(TextLeaf("alpha beta"))),
          ParagraphBlock(listOf(TextLeaf("gamma delta"))),
        )
      )
    val startSelection =
      ReaderSelector.startSelectionAtPageStart(
        page = fixture.page,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(startSelection)
    val updatedSelection =
      ReaderSelector.updateSelectionToPageEnd(
        page = fixture.page,
        selection = startSelection,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(updatedSelection)
    assertEquals("alpha beta\n\ngamma delta", updatedSelection.text)
  }

  @Test
  fun updateSelectionToPageStart_extendsSelectionToFirstVisibleWord() {
    val fixture =
      layoutDocument(
        listOf(
          ParagraphBlock(listOf(TextLeaf("alpha beta"))),
          ParagraphBlock(listOf(TextLeaf("gamma delta"))),
        )
      )
    val startSelection =
      ReaderSelector.startSelectionAtPageEnd(
        page = fixture.page,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(startSelection)
    val updatedSelection =
      ReaderSelector.updateSelectionToPageStart(
        page = fixture.page,
        selection = startSelection,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(updatedSelection)
    assertEquals("alpha beta\n\ngamma delta", updatedSelection.text)
  }

  @Test
  fun updateSelectionTo_extendsSelectionFromAnchorWord() {
    val fixture = layoutDocument("this book")
    val entry = fixture.page.entries.single() as PageEntry.FullBlock
    val thisOffset = entry.layout.text.indexOf("this")
    val bookOffset = entry.layout.text.indexOf("book")
    val touchY =
      fixture.verticalMarginPx +
        entry.yOffsetPx +
        (entry.layout.getLineTop(0) + entry.layout.getLineBottom(0)) / 2f
    val startSelection =
      ReaderSelector.startSelectionAt(
        page = fixture.page,
        xPx =
          fixture.horizontalMarginPx +
            entry.drawOffsetXPx +
            entry.layout.paint.measureText(entry.layout.text, 0, thisOffset + 2),
        yPx = touchY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(startSelection)
    val updatedSelection =
      ReaderSelector.updateSelectionTo(
        page = fixture.page,
        selection = startSelection,
        xPx =
          fixture.horizontalMarginPx +
            entry.drawOffsetXPx +
            entry.layout.paint.measureText(entry.layout.text, 0, bookOffset + 2),
        yPx = touchY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(updatedSelection)
    assertEquals("this book", updatedSelection.text)
    assertEquals(thisOffset until bookOffset + "book".length, updatedSelection.range)
    assertEquals(thisOffset until thisOffset + "this".length, updatedSelection.anchorRange)
  }

  @Test
  fun updateSelectionTo_extendsSelectionWhenDraggingVertically() {
    val fixture = layoutDocument("one two\nthree four\nfive six")
    val entry = fixture.page.entries.single() as PageEntry.FullBlock
    val oneOffset = entry.layout.text.indexOf("one")
    val threeOffset = entry.layout.text.indexOf("three")
    val startX =
      fixture.horizontalMarginPx +
        entry.drawOffsetXPx +
        entry.layout.paint.measureText(entry.layout.text, 0, oneOffset + 1)
    val startY =
      fixture.verticalMarginPx +
        entry.yOffsetPx +
        (entry.layout.getLineTop(0) + entry.layout.getLineBottom(0)) / 2f
    val lineTwoY =
      fixture.verticalMarginPx +
        entry.yOffsetPx +
        (entry.layout.getLineTop(1) + entry.layout.getLineBottom(1)) / 2f

    val startSelection =
      ReaderSelector.startSelectionAt(
        page = fixture.page,
        xPx = startX,
        yPx = startY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(startSelection)
    val updatedSelection =
      ReaderSelector.updateSelectionTo(
        page = fixture.page,
        selection = startSelection,
        xPx = startX,
        yPx = lineTwoY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(updatedSelection)
    assertEquals("one two\nthree", updatedSelection.text)
    assertEquals(oneOffset until threeOffset + "three".length, updatedSelection.range)
  }

  @Test
  fun updateSelectionTo_clampsHorizontalDragToNearestWordOnLine() {
    val fixture = layoutDocument("one two\nthree four")
    val entry = fixture.page.entries.single() as PageEntry.FullBlock
    val oneOffset = entry.layout.text.indexOf("one")
    val fourOffset = entry.layout.text.indexOf("four")
    val startY =
      fixture.verticalMarginPx +
        entry.yOffsetPx +
        (entry.layout.getLineTop(0) + entry.layout.getLineBottom(0)) / 2f
    val lineTwoY =
      fixture.verticalMarginPx +
        entry.yOffsetPx +
        (entry.layout.getLineTop(1) + entry.layout.getLineBottom(1)) / 2f
    val startSelection =
      ReaderSelector.startSelectionAt(
        page = fixture.page,
        xPx =
          fixture.horizontalMarginPx +
            entry.drawOffsetXPx +
            entry.layout.paint.measureText(entry.layout.text, 0, oneOffset + 1),
        yPx = startY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(startSelection)
    val updatedSelection =
      ReaderSelector.updateSelectionTo(
        page = fixture.page,
        selection = startSelection,
        xPx = fixture.horizontalMarginPx + entry.drawOffsetXPx + entry.layout.width + 80f,
        yPx = lineTwoY,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(updatedSelection)
    assertEquals("one two\nthree four", updatedSelection.text)
    assertEquals(oneOffset until fourOffset + "four".length, updatedSelection.range)
  }

  @Test
  fun updateSelectionTo_extendsSelectionAcrossParagraphEntries() {
    val fixture =
      layoutDocument(
        listOf(ParagraphBlock(listOf(TextLeaf("alpha"))), ParagraphBlock(listOf(TextLeaf("beta"))))
      )
    val firstEntry = fixture.page.entries[0] as PageEntry.FullBlock
    val secondEntry = fixture.page.entries[1] as PageEntry.FullBlock

    val startSelection =
      ReaderSelector.startSelectionAt(
        page = fixture.page,
        xPx =
          fixture.horizontalMarginPx +
            firstEntry.drawOffsetXPx +
            firstEntry.layout.paint.measureText(firstEntry.layout.text, 0, 2),
        yPx =
          fixture.verticalMarginPx +
            firstEntry.yOffsetPx +
            (firstEntry.layout.getLineTop(0) + firstEntry.layout.getLineBottom(0)) / 2f,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(startSelection)
    val updatedSelection =
      ReaderSelector.updateSelectionTo(
        page = fixture.page,
        selection = startSelection,
        xPx =
          fixture.horizontalMarginPx +
            secondEntry.drawOffsetXPx +
            secondEntry.layout.paint.measureText(secondEntry.layout.text, 0, 2),
        yPx =
          fixture.verticalMarginPx +
            secondEntry.yOffsetPx +
            (secondEntry.layout.getLineTop(0) + secondEntry.layout.getLineBottom(0)) / 2f,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(updatedSelection)
    assertEquals("alpha\n\nbeta", updatedSelection.text)
    assertEquals(2, updatedSelection.rects.size)
  }

  @Test
  fun updateSelectionTo_extendsSelectionAcrossParagraphEntriesInReverse() {
    val fixture =
      layoutDocument(
        listOf(ParagraphBlock(listOf(TextLeaf("alpha"))), ParagraphBlock(listOf(TextLeaf("beta"))))
      )
    val firstEntry = fixture.page.entries[0] as PageEntry.FullBlock
    val secondEntry = fixture.page.entries[1] as PageEntry.FullBlock

    val startSelection =
      ReaderSelector.startSelectionAt(
        page = fixture.page,
        xPx =
          fixture.horizontalMarginPx +
            secondEntry.drawOffsetXPx +
            secondEntry.layout.paint.measureText(secondEntry.layout.text, 0, 2),
        yPx =
          fixture.verticalMarginPx +
            secondEntry.yOffsetPx +
            (secondEntry.layout.getLineTop(0) + secondEntry.layout.getLineBottom(0)) / 2f,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(startSelection)
    val updatedSelection =
      ReaderSelector.updateSelectionTo(
        page = fixture.page,
        selection = startSelection,
        xPx =
          fixture.horizontalMarginPx +
            firstEntry.drawOffsetXPx +
            firstEntry.layout.paint.measureText(firstEntry.layout.text, 0, 2),
        yPx =
          fixture.verticalMarginPx +
            firstEntry.yOffsetPx +
            (firstEntry.layout.getLineTop(0) + firstEntry.layout.getLineBottom(0)) / 2f,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(updatedSelection)
    assertEquals("alpha\n\nbeta", updatedSelection.text)
    assertEquals(2, updatedSelection.rects.size)
  }

  @Test
  fun updateSelectionTo_extendsParagraphRangeAcrossMultipleListItemEntries() {
    val fixture =
      layoutDocument(
        listOf(
          ParagraphBlock(listOf(TextLeaf("alpha"))),
          ListBlock(
            ordered = false,
            items =
              listOf(
                ListItem(listOf(ParagraphBlock(listOf(TextLeaf("beta"))))),
                ListItem(listOf(ParagraphBlock(listOf(TextLeaf("gamma"))))),
              ),
          ),
          ParagraphBlock(listOf(TextLeaf("delta"))),
        )
      )
    val firstEntry = fixture.page.entries.first() as PageEntry.FullBlock
    val lastEntry = fixture.page.entries.last() as PageEntry.FullBlock

    val startSelection =
      ReaderSelector.startSelectionAt(
        page = fixture.page,
        xPx =
          fixture.horizontalMarginPx +
            firstEntry.drawOffsetXPx +
            firstEntry.layout.paint.measureText(firstEntry.layout.text, 0, 2),
        yPx =
          fixture.verticalMarginPx +
            firstEntry.yOffsetPx +
            (firstEntry.layout.getLineTop(0) + firstEntry.layout.getLineBottom(0)) / 2f,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(startSelection)
    val updatedSelection =
      ReaderSelector.updateSelectionTo(
        page = fixture.page,
        selection = startSelection,
        xPx =
          fixture.horizontalMarginPx +
            lastEntry.drawOffsetXPx +
            lastEntry.layout.paint.measureText(lastEntry.layout.text, 0, 2),
        yPx =
          fixture.verticalMarginPx +
            lastEntry.yOffsetPx +
            (lastEntry.layout.getLineTop(0) + lastEntry.layout.getLineBottom(0)) / 2f,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(updatedSelection)
    assertEquals("alpha\n\nbeta\n\ngamma\n\ndelta", updatedSelection.text)
  }

  @Test
  fun updateSelectionTo_extendsDragAcrossSeparateListItemEntries() {
    val fixture =
      layoutDocument(
        listOf(
          ListBlock(
            ordered = false,
            items =
              listOf(
                ListItem(listOf(ParagraphBlock(listOf(TextLeaf("alpha"))))),
                ListItem(listOf(ParagraphBlock(listOf(TextLeaf("beta"))))),
              ),
          )
        )
      )
    val firstEntry = fixture.page.entries[0] as PageEntry.FullBlock
    val secondEntry = fixture.page.entries[1] as PageEntry.FullBlock
    assertEquals(firstEntry.blockIndex, secondEntry.blockIndex)
    assertTrue(firstEntry.selectionId != secondEntry.selectionId)

    val startSelection =
      ReaderSelector.startSelectionAt(
        page = fixture.page,
        xPx =
          fixture.horizontalMarginPx +
            firstEntry.drawOffsetXPx +
            firstEntry.layout.paint.measureText(firstEntry.layout.text, 0, 2),
        yPx =
          fixture.verticalMarginPx +
            firstEntry.yOffsetPx +
            (firstEntry.layout.getLineTop(0) + firstEntry.layout.getLineBottom(0)) / 2f,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(startSelection)
    val updatedSelection =
      ReaderSelector.updateSelectionTo(
        page = fixture.page,
        selection = startSelection,
        xPx =
          fixture.horizontalMarginPx +
            secondEntry.drawOffsetXPx +
            secondEntry.layout.paint.measureText(secondEntry.layout.text, 0, 2),
        yPx =
          fixture.verticalMarginPx +
            secondEntry.yOffsetPx +
            (secondEntry.layout.getLineTop(0) + secondEntry.layout.getLineBottom(0)) / 2f,
        horizontalMarginPx = fixture.horizontalMarginPx,
        verticalMarginPx = fixture.verticalMarginPx,
        locale = Locale.US,
      )

    requireNotNull(updatedSelection)
    assertEquals("alpha\n\nbeta", updatedSelection.text)
  }

  private fun layoutDocument(
    text: String,
    viewport: ReaderViewport = ReaderViewport(widthPx = 500, heightPx = 600, density = 2f),
    justify: Boolean = false,
  ): LayoutFixture =
    layoutDocument(listOf(ParagraphBlock(listOf(TextLeaf(text)))), viewport, justify)

  private fun layoutDocument(
    blocks: List<ReaderBlock>,
    viewport: ReaderViewport = ReaderViewport(widthPx = 500, heightPx = 600, density = 2f),
    justify: Boolean = false,
    pagePicker:
      (
        List<com.charliesbot.kanshu.navigator.engine.ReaderPage>
      ) -> com.charliesbot.kanshu.navigator.engine.ReaderPage =
      { pages ->
        pages.first()
      },
  ): LayoutFixture {
    val styleResolver = BlockStyleResolver(ReaderPreferences(), Typeface.DEFAULT, viewport.density)
    val horizontalMarginPx = styleResolver.horizontalMarginPx()
    val verticalMarginPx = styleResolver.verticalMarginPx()
    val pages =
      ReaderLayoutEngine()
        .layout(
          document = ReaderDocument(blocks = blocks),
          viewport = viewport,
          horizontalMarginPx = horizontalMarginPx,
          verticalMarginPx = verticalMarginPx,
          justify = justify,
          styleResolver = styleResolver::resolve,
        )
    return LayoutFixture(
      page = pagePicker(pages),
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
    )
  }

  private data class LayoutFixture(
    val page: com.charliesbot.kanshu.navigator.engine.ReaderPage,
    val horizontalMarginPx: Float,
    val verticalMarginPx: Float,
  )

  private fun CharSequence.measureText(paint: TextPaint, start: Int, end: Int): Float {
    if (start >= end) return 0f
    if (this !is Spanned) return paint.measureText(this, start, end)

    var measuredWidth = 0f
    var segmentStart = start
    while (segmentStart < end) {
      val segmentEnd = nextSpanTransition(segmentStart, end, MetricAffectingSpan::class.java)
      val segmentPaint = TextPaint(paint)
      getSpans(segmentStart, segmentEnd, MetricAffectingSpan::class.java).forEach { span ->
        span.updateMeasureState(segmentPaint)
      }
      measuredWidth += segmentPaint.measureText(this, segmentStart, segmentEnd)
      segmentStart = segmentEnd
    }
    return measuredWidth
  }
}
