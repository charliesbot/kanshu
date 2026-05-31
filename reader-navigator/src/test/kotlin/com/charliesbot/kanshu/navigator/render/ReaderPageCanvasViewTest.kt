package com.charliesbot.kanshu.navigator.render

import android.os.Looper
import android.text.Layout
import android.text.TextPaint
import android.view.MotionEvent
import com.charliesbot.kanshu.navigator.engine.BlockStyle
import com.charliesbot.kanshu.navigator.engine.PageEntry
import com.charliesbot.kanshu.navigator.engine.ReaderPage
import com.charliesbot.kanshu.navigator.engine.StaticLayoutFactory
import com.charliesbot.kanshu.navigator.selection.ReaderSelector
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ReaderPageCanvasViewTest {
  @Test
  fun performClick_invokesCenterTapZoneForAccessibilityClicks() {
    val view = ReaderPageCanvasView(RuntimeEnvironment.getApplication())
    val tappedZones = mutableListOf<ReaderPageTapZone>()
    view.setPage(
      page = ReaderPage(emptyList()),
      horizontalMarginPx = 0f,
      verticalMarginPx = 0f,
      onTapZone = tappedZones::add,
    )

    val handled = view.performClick()

    assertTrue(handled)
    assertEquals(listOf(ReaderPageTapZone.Center), tappedZones)
  }

  @Test
  fun isInForwardSelectionAdvanceZone_matchesBottomEdge() {
    assertEquals(null, 500f.selectionPageTurnDirection(height = 1000, density = 2f))
    assertEquals(
      SelectionPageTurnDirection.Previous,
      100f.selectionPageTurnDirection(height = 1000, density = 2f),
    )
    assertEquals(
      SelectionPageTurnDirection.Next,
      900f.selectionPageTurnDirection(height = 1000, density = 2f),
    )
  }

  @Test
  fun selectionDragNearBottom_invokesDelayedPageTurn() {
    val view = ReaderPageCanvasView(RuntimeEnvironment.getApplication())
    val advances = mutableListOf<String>()
    view.layout(0, 0, 500, 1000)
    view.setPage(
      page = selectablePage(),
      horizontalMarginPx = 0f,
      verticalMarginPx = 0f,
      onSelectionPageTurn = { direction, selectedText, _, _ ->
        advances += "$direction:$selectedText"
        true
      },
      seedSelectionAtPageStart = true,
    )

    view.onTouchEvent(moveEvent(y = 950f))
    shadowOf(Looper.getMainLooper()).idleFor(899, TimeUnit.MILLISECONDS)
    assertEquals(emptyList<String>(), advances)

    shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)

    assertEquals(listOf("${SelectionPageTurnDirection.Next}:alpha beta"), advances)
  }

  @Test
  fun selectionDragNearTop_invokesDelayedPreviousPageTurn() {
    val view = ReaderPageCanvasView(RuntimeEnvironment.getApplication())
    val turns = mutableListOf<String>()
    view.layout(0, 0, 500, 1000)
    view.setPage(
      page = selectablePage(),
      horizontalMarginPx = 0f,
      verticalMarginPx = 0f,
      onSelectionPageTurn = { direction, selectedText, _, _ ->
        turns += "$direction:$selectedText"
        true
      },
      seedSelectionAtPageEnd = true,
    )

    view.onTouchEvent(moveEvent(y = 40f))
    shadowOf(Looper.getMainLooper()).idleFor(899, TimeUnit.MILLISECONDS)
    assertEquals(emptyList<String>(), turns)

    shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)

    assertEquals(listOf("${SelectionPageTurnDirection.Previous}:alpha beta"), turns)
  }

  @Test
  fun release_cancelsPendingSelectionPageTurn() {
    val view = ReaderPageCanvasView(RuntimeEnvironment.getApplication())
    val advances = mutableListOf<String>()
    view.layout(0, 0, 500, 1000)
    view.setPage(
      page = selectablePage(),
      horizontalMarginPx = 0f,
      verticalMarginPx = 0f,
      onSelectionPageTurn = { _, selectedText, _, _ ->
        advances += selectedText
        true
      },
      seedSelectionAtPageStart = true,
    )

    view.onTouchEvent(moveEvent(y = 950f))
    view.release()
    shadowOf(Looper.getMainLooper()).idleFor(900, TimeUnit.MILLISECONDS)

    assertEquals(emptyList<String>(), advances)
  }

  @Test
  fun rejectedSelectionPageTurnCanBeRetried() {
    val view = ReaderPageCanvasView(RuntimeEnvironment.getApplication())
    var attempts = 0
    view.layout(0, 0, 500, 1000)
    view.setPage(
      page = selectablePage(),
      horizontalMarginPx = 0f,
      verticalMarginPx = 0f,
      onSelectionPageTurn = { _, _, _, _ ->
        attempts++
        false
      },
      seedSelectionAtPageStart = true,
    )

    view.onTouchEvent(moveEvent(y = 950f))
    shadowOf(Looper.getMainLooper()).idleFor(900, TimeUnit.MILLISECONDS)
    view.onTouchEvent(moveEvent(y = 950f))
    shadowOf(Looper.getMainLooper()).idleFor(900, TimeUnit.MILLISECONDS)

    assertEquals(2, attempts)
  }

  @Test
  fun acceptedSelectionPageTurnRearmsWhenNextPageIsSeededAtHeldBottomEdge() {
    val view = ReaderPageCanvasView(RuntimeEnvironment.getApplication())
    val advances = mutableListOf<String>()
    view.layout(0, 0, 500, 1000)
    view.setPage(
      page = selectablePage("alpha beta"),
      horizontalMarginPx = 0f,
      verticalMarginPx = 0f,
      onSelectionPageTurn = { direction, selectedText, _, _ ->
        assertEquals(SelectionPageTurnDirection.Next, direction)
        advances += selectedText
        true
      },
      seedSelectionAtPageStart = true,
    )

    view.onTouchEvent(moveEvent(y = 950f))
    shadowOf(Looper.getMainLooper()).idleFor(900, TimeUnit.MILLISECONDS)
    view.setPage(
      page = selectablePage("gamma delta"),
      horizontalMarginPx = 0f,
      verticalMarginPx = 0f,
      onSelectionPageTurn = { direction, selectedText, _, _ ->
        assertEquals(SelectionPageTurnDirection.Next, direction)
        advances += selectedText
        true
      },
      selectionTextPrefix = "${advances.single()}\n\n",
      seedSelectionAtPageStart = true,
    )
    shadowOf(Looper.getMainLooper()).idleFor(899, TimeUnit.MILLISECONDS)
    assertEquals(listOf("alpha beta"), advances)

    shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)

    assertEquals(listOf("alpha beta", "alpha beta\n\ngamma delta"), advances)
  }

  @Test
  fun acceptedSelectionPageTurnRearmsWhenPreviousPageIsSeededAtHeldTopEdge() {
    val view = ReaderPageCanvasView(RuntimeEnvironment.getApplication())
    val turns = mutableListOf<String>()
    view.layout(0, 0, 500, 1000)
    view.setPage(
      page = selectablePage("gamma delta"),
      horizontalMarginPx = 0f,
      verticalMarginPx = 0f,
      onSelectionPageTurn = { direction, selectedText, _, _ ->
        turns += "$direction:$selectedText"
        true
      },
      seedSelectionAtPageEnd = true,
    )

    view.onTouchEvent(moveEvent(y = 40f))
    shadowOf(Looper.getMainLooper()).idleFor(900, TimeUnit.MILLISECONDS)
    view.setPage(
      page = selectablePage("alpha beta"),
      horizontalMarginPx = 0f,
      verticalMarginPx = 0f,
      onSelectionPageTurn = { direction, selectedText, _, _ ->
        turns += "$direction:$selectedText"
        true
      },
      selectionTextSuffix = "\n\n${turns.single().substringAfter(":")}",
      seedSelectionAtPageEnd = true,
    )
    shadowOf(Looper.getMainLooper()).idleFor(899, TimeUnit.MILLISECONDS)
    assertEquals(listOf("${SelectionPageTurnDirection.Previous}:gamma delta"), turns)

    shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)

    assertEquals(
      listOf(
        "${SelectionPageTurnDirection.Previous}:gamma delta",
        "${SelectionPageTurnDirection.Previous}:alpha beta\n\ngamma delta",
      ),
      turns,
    )
  }

  @Test
  fun restoredSelectionPageTurnRearmsAtHeldTopEdge() {
    val view = ReaderPageCanvasView(RuntimeEnvironment.getApplication())
    val turns = mutableListOf<String>()
    val page = selectablePage("alpha beta")
    val restoredSelection =
      ReaderSelector.startSelectionAtPageEnd(
        page = page,
        horizontalMarginPx = 0f,
        verticalMarginPx = 0f,
      )
    requireNotNull(restoredSelection)
    view.layout(0, 0, 500, 1000)
    view.setPage(
      page = selectablePage("gamma delta"),
      horizontalMarginPx = 0f,
      verticalMarginPx = 0f,
      seedSelectionAtPageEnd = true,
    )
    view.onTouchEvent(moveEvent(y = 40f))

    view.setPage(
      page = page,
      horizontalMarginPx = 0f,
      verticalMarginPx = 0f,
      onSelectionPageTurn = { direction, selectedText, _, _ ->
        turns += "$direction:$selectedText"
        true
      },
      restoredSelection = restoredSelection,
    )
    shadowOf(Looper.getMainLooper()).idleFor(899, TimeUnit.MILLISECONDS)
    assertEquals(emptyList<String>(), turns)

    shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)

    assertEquals(listOf("${SelectionPageTurnDirection.Previous}:beta"), turns)
  }

  @Test
  fun longPressAfterPrefixedSelectionStartsFreshSelectionText() {
    val view = ReaderPageCanvasView(RuntimeEnvironment.getApplication())
    val selectedTexts = mutableListOf<String>()
    var clearCount = 0
    val page = selectablePage("alpha beta")
    view.layout(0, 0, 500, 1000)
    view.setPage(
      page = page,
      horizontalMarginPx = 0f,
      verticalMarginPx = 0f,
      onTextSelected = { text, _ -> selectedTexts += text },
      onSelectionCleared = { clearCount++ },
      selectionTextPrefix = "previous page\n\n",
      seedSelectionAtPageStart = true,
    )
    selectedTexts.clear()
    val firstEntry = page.entries.single() as PageEntry.FullBlock
    val touchX = firstEntry.layout.paint.measureText(firstEntry.layout.text, 0, 2)
    val touchY = (firstEntry.layout.getLineTop(0) + firstEntry.layout.getLineBottom(0)) / 2f

    view.startSelectionFromLongPress(xPx = touchX, yPx = touchY)

    assertEquals("alpha", selectedTexts.last())
    assertEquals(1, clearCount)
  }

  @Test
  fun longPressAfterSuffixedSelectionStartsFreshSelectionText() {
    val view = ReaderPageCanvasView(RuntimeEnvironment.getApplication())
    val selectedTexts = mutableListOf<String>()
    var clearCount = 0
    val page = selectablePage("alpha beta")
    view.layout(0, 0, 500, 1000)
    view.setPage(
      page = page,
      horizontalMarginPx = 0f,
      verticalMarginPx = 0f,
      onTextSelected = { text, _ -> selectedTexts += text },
      onSelectionCleared = { clearCount++ },
      selectionTextSuffix = "\n\nnext page",
      seedSelectionAtPageEnd = true,
    )
    selectedTexts.clear()
    val firstEntry = page.entries.single() as PageEntry.FullBlock
    val touchX = firstEntry.layout.paint.measureText(firstEntry.layout.text, 0, 2)
    val touchY = (firstEntry.layout.getLineTop(0) + firstEntry.layout.getLineBottom(0)) / 2f

    view.startSelectionFromLongPress(xPx = touchX, yPx = touchY)

    assertEquals("alpha", selectedTexts.last())
    assertEquals(1, clearCount)
  }

  private fun selectablePage(text: String = "alpha beta"): ReaderPage {
    val layout =
      StaticLayoutFactory.build(
        text = text,
        style =
          BlockStyle(
            paint = TextPaint().apply { textSize = 24f },
            lineSpacingMultiplier = 1f,
            lineSpacingAdd = 0f,
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE,
            alignment = Layout.Alignment.ALIGN_NORMAL,
            breakStrategy = Layout.BREAK_STRATEGY_SIMPLE,
            indentPx = 0f,
            prefixWidthPx = 0f,
            marginTopPx = 0f,
            marginBottomPx = 0f,
          ),
        contentWidthPx = 400,
        justify = false,
      )
    return ReaderPage(
      listOf(
        PageEntry.FullBlock(
          blockIndex = 0,
          yOffsetPx = 0f,
          visibleHeightPx = layout.height.toFloat(),
          drawOffsetXPx = 0f,
          layout = layout,
        )
      )
    )
  }

  private fun moveEvent(y: Float): MotionEvent =
    MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 24f, y, 0)
}
