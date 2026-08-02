package com.charliesbot.kanshu.navigator.render

import android.graphics.RectF
import com.charliesbot.kanshu.navigator.ReaderSelectionInfo
import com.charliesbot.kanshu.navigator.engine.ReaderPage
import com.charliesbot.kanshu.navigator.selection.ReaderSelector
import com.charliesbot.kanshu.navigator.selection.TextSelection
import java.util.Locale

internal class ReaderPageSelectionController(
  private val scheduler: SelectionPageTurnScheduler,
  private val viewportHeightPx: () -> Int,
  private val density: () -> Float,
  private val invalidate: () -> Unit,
) {
  private var page: ReaderPage? = null
  private var horizontalMarginPx = 0f
  private var verticalMarginPx = 0f
  private var selectionTextPrefix = ""
  private var selectionTextSuffix = ""
  private var selectionLocale: Locale = Locale.getDefault()
  private var onTextSelected: ((ReaderSelectionInfo) -> Unit)? = null
  // Stream range of the word the selection was anchored on, captured once when the selection
  // starts and never revised. The full selection is always contiguous from the anchor to the
  // focus, so unioning this with the on-page range reconstructs the whole span even when the
  // anchor has scrolled onto another page — and, unlike accumulating every reported range, it
  // shrinks correctly when the reader drags back toward the anchor.
  private var anchorStreamRange: IntRange? = null
  private var onSelectionCleared: (() -> Unit)? = null
  private var onSelectionPageTurn:
    ((SelectionPageTurnDirection, String, String, TextSelection) -> Boolean)? =
    null
  private var selection: TextSelection? = null
  private var lastDragYPx: Float? = null

  val selectionRects: List<RectF>
    get() = selection?.rects.orEmpty()

  fun setPage(
    page: ReaderPage,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
    onTextSelected: ((ReaderSelectionInfo) -> Unit)?,
    onSelectionCleared: (() -> Unit)?,
    onSelectionPageTurn: ((SelectionPageTurnDirection, String, String, TextSelection) -> Boolean)?,
    selectionTextPrefix: String,
    selectionTextSuffix: String,
    selectionLocale: Locale,
    restoredSelection: TextSelection?,
    seedSelectionAtPageStart: Boolean,
    seedSelectionAtPageEnd: Boolean,
  ): Boolean {
    val pageChanged = this.page !== page
    this.page = page
    this.horizontalMarginPx = horizontalMarginPx
    this.verticalMarginPx = verticalMarginPx
    this.onTextSelected = onTextSelected
    this.onSelectionCleared = onSelectionCleared
    this.onSelectionPageTurn = onSelectionPageTurn
    this.selectionTextPrefix = selectionTextPrefix
    this.selectionTextSuffix = selectionTextSuffix
    this.selectionLocale = selectionLocale

    if (pageChanged) {
      scheduler.cancel()
      selection =
        when {
          restoredSelection != null -> restoredSelection
          seedSelectionAtPageStart -> startSelectionAtPageStart(page)
          seedSelectionAtPageEnd -> startSelectionAtPageEnd(page)
          else -> {
            clearSelection()
            null
          }
        }
      if (selection != null) {
        extendSeededSelectionToHeldEdge(extendSelectionToEdge = restoredSelection == null)
      }
    }
    selection?.let { textSelection ->
      notifySelected(textSelection)
    }
    return pageChanged && selection != null
  }

  fun clearSelection(): Boolean {
    if (selection == null) return false
    scheduler.cancel()
    selection = null
    anchorStreamRange = null
    onSelectionCleared?.invoke()
    invalidate()
    return true
  }

  fun clearSelectionCarryOver() {
    if (selection == null && selectionTextPrefix.isEmpty() && selectionTextSuffix.isEmpty()) return
    scheduler.cancel()
    selection = null
    anchorStreamRange = null
    selectionTextPrefix = ""
    selectionTextSuffix = ""
    onSelectionCleared?.invoke()
  }

  fun startSelectionFromLongPress(xPx: Float, yPx: Float) {
    val currentPage = page ?: return
    clearSelectionCarryOver()
    anchorStreamRange = null
    val textSelection =
      ReaderSelector.startSelectionAt(
        page = currentPage,
        xPx = xPx,
        yPx = yPx,
        horizontalMarginPx = horizontalMarginPx,
        verticalMarginPx = verticalMarginPx,
        locale = selectionLocale,
      )
    selection = textSelection
    if (textSelection == null) {
      onSelectionCleared?.invoke()
    } else {
      notifySelected(textSelection)
    }
    invalidate()
  }

  fun updateSelection(xPx: Float, yPx: Float) {
    lastDragYPx = yPx
    if (updateSelectionPageTurn(yPx)) {
      selection?.let { updatedSelection ->
        notifySelected(updatedSelection)
      }
      invalidate()
      return
    }
    val currentPage = page ?: return
    val currentSelection = selection ?: return
    val updatedSelection =
      ReaderSelector.updateSelectionTo(
        page = currentPage,
        selection = currentSelection,
        xPx = xPx,
        yPx = yPx,
        horizontalMarginPx = horizontalMarginPx,
        verticalMarginPx = verticalMarginPx,
        locale = selectionLocale,
      ) ?: return
    if (updatedSelection == currentSelection) return
    selection = updatedSelection
    notifySelected(updatedSelection)
    invalidate()
  }

  fun finishDrag() {
    lastDragYPx = null
    scheduler.cancel()
  }

  fun release() {
    scheduler.cancel()
    selection = null
    anchorStreamRange = null
    lastDragYPx = null
    onTextSelected = null
    onSelectionCleared = null
    onSelectionPageTurn = null
  }

  private fun startSelectionAtPageStart(page: ReaderPage): TextSelection? =
    ReaderSelector.startSelectionAtPageStart(
      page = page,
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
      locale = selectionLocale,
    )

  private fun startSelectionAtPageEnd(page: ReaderPage): TextSelection? =
    ReaderSelector.startSelectionAtPageEnd(
      page = page,
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
      locale = selectionLocale,
    )

  private fun extendSeededSelectionToHeldEdge(extendSelectionToEdge: Boolean) {
    val y = lastDragYPx ?: return
    val direction =
      y.selectionPageTurnDirection(height = viewportHeightPx(), density = density()) ?: return
    if (selection == null) return
    if (extendSelectionToEdge) {
      extendSelectionToPageEdge(direction)
    }
    scheduleSelectionPageTurn(direction)
  }

  private fun updateSelectionPageTurn(yPx: Float): Boolean {
    if (selection == null || scheduler.isAwaitingPageTurn) return false
    val direction = yPx.selectionPageTurnDirection(height = viewportHeightPx(), density = density())
    if (direction != null) {
      extendSelectionToPageEdge(direction)
      scheduleSelectionPageTurn(direction)
      return true
    } else {
      scheduler.cancel()
      return false
    }
  }

  private fun scheduleSelectionPageTurn(direction: SelectionPageTurnDirection) {
    scheduler.schedule(direction) { turnDirection ->
      val currentSelection = selection ?: return@schedule false
      val pageSelectedText = currentSelection.text
      val selectedText = currentSelectedText() ?: return@schedule false
      onSelectionPageTurn?.invoke(
        turnDirection,
        selectedText,
        pageSelectedText,
        currentSelection,
      ) == true
    }
  }

  private fun extendSelectionToPageEdge(direction: SelectionPageTurnDirection) {
    val currentPage = page ?: return
    val currentSelection = selection ?: return
    selection = currentSelection.selectionAtPageEdge(currentPage, direction) ?: currentSelection
  }

  private fun TextSelection.selectionAtPageEdge(
    page: ReaderPage,
    direction: SelectionPageTurnDirection,
  ): TextSelection? =
    when (direction) {
      SelectionPageTurnDirection.Previous ->
        ReaderSelector.updateSelectionToPageStart(
          page = page,
          selection = this,
          horizontalMarginPx = horizontalMarginPx,
          verticalMarginPx = verticalMarginPx,
          locale = selectionLocale,
        )
      SelectionPageTurnDirection.Next ->
        ReaderSelector.updateSelectionToPageEnd(
          page = page,
          selection = this,
          horizontalMarginPx = horizontalMarginPx,
          verticalMarginPx = verticalMarginPx,
          locale = selectionLocale,
        )
    }

  /** Reports the selection spanning the anchor and whatever of it is on this page. */
  private fun notifySelected(textSelection: TextSelection) {
    val callback = onTextSelected ?: return
    val pageRange = textSelection.streamRange
    if (anchorStreamRange == null && !pageRange.isEmpty()) {
      // Only a fresh long press starts a selection; a page carried into mid-drag keeps the
      // anchor it began with.
      anchorStreamRange = pageRange
    }
    val anchor = anchorStreamRange
    val range =
      when {
        // Nothing anchored yet, so there is no range to report.
        anchor == null -> IntRange.EMPTY
        // The selection is anchored but sits entirely on another page.
        pageRange.isEmpty() -> anchor
        else -> minOf(anchor.first, pageRange.first)..maxOf(anchor.last, pageRange.last)
      }
    callback(
      ReaderSelectionInfo(
        text = currentSelectedText().orEmpty(),
        anchor = textSelection.anchor,
        startCharOffset = if (range.isEmpty()) 0 else range.first,
        endCharOffset = if (range.isEmpty()) 0 else range.last + 1,
      )
    )
  }

  private fun currentSelectedText(): String? {
    val currentSelection = selection ?: return null
    return selectionTextPrefix + currentSelection.text + selectionTextSuffix
  }
}
