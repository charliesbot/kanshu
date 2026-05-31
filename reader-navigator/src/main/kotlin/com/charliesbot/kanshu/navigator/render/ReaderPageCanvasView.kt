package com.charliesbot.kanshu.navigator.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.charliesbot.kanshu.navigator.engine.ReaderPage
import com.charliesbot.kanshu.navigator.selection.ReaderSelector
import com.charliesbot.kanshu.navigator.selection.TextSelection
import java.util.Locale

private const val TAG = "ReaderPageCanvasView"
private const val SELECTION_PAGE_TURN_DELAY_MS = 900L
private const val SELECTION_PAGE_TURN_EDGE_DP = 64f

internal enum class ReaderPageTapZone {
  Previous,
  Center,
  Next,
}

internal enum class SelectionPageTurnDirection {
  Previous,
  Next,
}

internal class ReaderPageCanvasView(context: Context) : View(context) {
  private var page: ReaderPage? = null
  private var horizontalMarginPx = 0f
  private var verticalMarginPx = 0f
  private var selection: TextSelection? = null
  private var onTapZone: ((ReaderPageTapZone) -> Unit)? = null
  private var onTextSelected: ((String, RectF) -> Unit)? = null
  private var onSelectionCleared: (() -> Unit)? = null
  private var onSelectionPageTurn:
    ((SelectionPageTurnDirection, String, String, TextSelection) -> Boolean)? =
    null
  private var selectionTextPrefix = ""
  private var selectionTextSuffix = ""
  private var selectionLocale: Locale = Locale.getDefault()
  private var handledLongPress = false
  private var pendingClickZone = ReaderPageTapZone.Center
  private var selectionPageTurnScheduled = false
  private var awaitingSelectionPageTurn = false
  private var scheduledSelectionPageTurnDirection: SelectionPageTurnDirection? = null
  private var lastDragXPx: Float? = null
  private var lastDragYPx: Float? = null
  private val selectionPageTurnHandler = Handler(Looper.getMainLooper())
  private val selectionPageTurnRunnable = Runnable {
    selectionPageTurnScheduled = false
    val direction = scheduledSelectionPageTurnDirection ?: return@Runnable
    scheduledSelectionPageTurnDirection = null
    val currentSelection = selection ?: return@Runnable
    val pageSelectedText = currentSelection.text
    val selectedText = currentSelectedText() ?: return@Runnable
    awaitingSelectionPageTurn =
      onSelectionPageTurn?.invoke(direction, selectedText, pageSelectedText, currentSelection) ==
        true
  }
  private val gestureDetector =
    GestureDetector(
      context,
      object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean {
          handledLongPress = false
          lastDragXPx = null
          lastDragYPx = null
          return true
        }

        override fun onSingleTapUp(event: MotionEvent): Boolean {
          if (handledLongPress) {
            handledLongPress = false
            return true
          }
          if (selection != null) {
            clearSelection()
            return true
          }
          pendingClickZone = event.tapZone(width)
          performClick()
          return true
        }

        override fun onLongPress(event: MotionEvent) {
          startSelectionFromLongPress(event.x, event.y)
        }
      },
    )

  fun setPage(
    page: ReaderPage,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
    onTapZone: ((ReaderPageTapZone) -> Unit)? = null,
    onTextSelected: ((String, RectF) -> Unit)? = null,
    onSelectionCleared: (() -> Unit)? = null,
    onSelectionPageTurn: ((SelectionPageTurnDirection, String, String, TextSelection) -> Boolean)? =
      null,
    selectionTextPrefix: String = "",
    selectionTextSuffix: String = "",
    selectionLocale: Locale = Locale.getDefault(),
    restoredSelection: TextSelection? = null,
    seedSelectionAtPageStart: Boolean = false,
    seedSelectionAtPageEnd: Boolean = false,
  ) {
    val pageChanged = this.page !== page
    this.page = page
    this.horizontalMarginPx = horizontalMarginPx
    this.verticalMarginPx = verticalMarginPx
    this.onTapZone = onTapZone
    this.onTextSelected = onTextSelected
    this.onSelectionCleared = onSelectionCleared
    this.onSelectionPageTurn = onSelectionPageTurn
    this.selectionTextPrefix = selectionTextPrefix
    this.selectionTextSuffix = selectionTextSuffix
    this.selectionLocale = selectionLocale

    if (pageChanged) {
      cancelSelectionPageTurn()
      selection =
        when {
          restoredSelection != null -> restoredSelection
          seedSelectionAtPageStart ->
            ReaderSelector.startSelectionAtPageStart(
              page = page,
              horizontalMarginPx = horizontalMarginPx,
              verticalMarginPx = verticalMarginPx,
              locale = selectionLocale,
            )
          seedSelectionAtPageEnd ->
            ReaderSelector.startSelectionAtPageEnd(
              page = page,
              horizontalMarginPx = horizontalMarginPx,
              verticalMarginPx = verticalMarginPx,
              locale = selectionLocale,
            )
          else -> {
            clearSelection()
            null
          }
        }
      if (selection != null) {
        handledLongPress = true
        extendSeededSelectionToHeldEdge(extendSelectionToEdge = restoredSelection == null)
      }
    }
    Log.d(TAG, "render entries=${page.entries.size}")
    selection?.let { textSelection ->
      onTextSelected?.invoke(currentSelectedText().orEmpty(), textSelection.anchor)
    }
    invalidate()
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val handledByDetector = gestureDetector.onTouchEvent(event)
    when (event.actionMasked) {
      MotionEvent.ACTION_MOVE -> {
        if (handledLongPress) {
          updateSelection(event)
          return true
        }
      }

      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        if (handledLongPress) {
          handledLongPress = false
          lastDragXPx = null
          lastDragYPx = null
          cancelSelectionPageTurn()
          return true
        }
      }
    }
    return handledByDetector || super.onTouchEvent(event)
  }

  override fun onDetachedFromWindow() {
    release()
    super.onDetachedFromWindow()
  }

  fun release() {
    cancelSelectionPageTurn()
    selection = null
    lastDragXPx = null
    lastDragYPx = null
    onTapZone = null
    onTextSelected = null
    onSelectionCleared = null
    onSelectionPageTurn = null
  }

  override fun performClick(): Boolean {
    super.performClick()
    onTapZone?.invoke(pendingClickZone)
    pendingClickZone = ReaderPageTapZone.Center
    return true
  }

  internal fun startSelectionFromLongPress(xPx: Float, yPx: Float) {
    val currentPage = page ?: return
    handledLongPress = true
    clearSelectionCarryOver()
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
      onTextSelected?.invoke(currentSelectedText().orEmpty(), textSelection.anchor)
    }
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val page = page ?: return
    PageRenderer.draw(
      canvas = canvas,
      page = page,
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
      selectionRects = selection?.rects.orEmpty(),
    )
  }

  private fun MotionEvent.tapZone(viewWidth: Int): ReaderPageTapZone {
    val thirdWidth = viewWidth / 3f
    return when {
      x < thirdWidth -> ReaderPageTapZone.Previous
      x > thirdWidth * 2f -> ReaderPageTapZone.Next
      else -> ReaderPageTapZone.Center
    }
  }

  private fun clearSelection() {
    if (selection == null) return
    cancelSelectionPageTurn()
    selection = null
    onSelectionCleared?.invoke()
    invalidate()
  }

  private fun clearSelectionCarryOver() {
    if (selection == null && selectionTextPrefix.isEmpty() && selectionTextSuffix.isEmpty()) return
    cancelSelectionPageTurn()
    selection = null
    selectionTextPrefix = ""
    selectionTextSuffix = ""
    onSelectionCleared?.invoke()
  }

  private fun updateSelection(event: MotionEvent) {
    lastDragXPx = event.x
    lastDragYPx = event.y
    if (updateSelectionPageTurn(event)) {
      selection?.let { updatedSelection ->
        onTextSelected?.invoke(currentSelectedText().orEmpty(), updatedSelection.anchor)
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
        xPx = event.x,
        yPx = event.y,
        horizontalMarginPx = horizontalMarginPx,
        verticalMarginPx = verticalMarginPx,
        locale = selectionLocale,
      ) ?: return
    if (updatedSelection == currentSelection) return
    selection = updatedSelection
    onTextSelected?.invoke(currentSelectedText().orEmpty(), updatedSelection.anchor)
    invalidate()
  }

  private fun extendSeededSelectionToHeldEdge(extendSelectionToEdge: Boolean) {
    val y = lastDragYPx ?: return
    val direction =
      y.selectionPageTurnDirection(height = height, density = resources.displayMetrics.density)
        ?: return
    if (selection == null) return
    if (extendSelectionToEdge) {
      extendSelectionToPageEdge(direction)
    }
    scheduleSelectionPageTurn(direction)
  }

  private fun updateSelectionPageTurn(event: MotionEvent): Boolean {
    if (selection == null || awaitingSelectionPageTurn) return false
    val direction =
      event.y.selectionPageTurnDirection(
        height = height,
        density = resources.displayMetrics.density,
      )
    if (direction != null) {
      extendSelectionToPageEdge(direction)
      scheduleSelectionPageTurn(direction)
      return true
    } else {
      cancelSelectionPageTurn()
      return false
    }
  }

  private fun scheduleSelectionPageTurn(direction: SelectionPageTurnDirection) {
    if (selectionPageTurnScheduled && scheduledSelectionPageTurnDirection == direction) return
    selectionPageTurnHandler.removeCallbacks(selectionPageTurnRunnable)
    scheduledSelectionPageTurnDirection = direction
    selectionPageTurnScheduled = true
    selectionPageTurnHandler.postDelayed(selectionPageTurnRunnable, SELECTION_PAGE_TURN_DELAY_MS)
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

  private fun cancelSelectionPageTurn() {
    selectionPageTurnScheduled = false
    awaitingSelectionPageTurn = false
    scheduledSelectionPageTurnDirection = null
    selectionPageTurnHandler.removeCallbacks(selectionPageTurnRunnable)
  }

  private fun currentSelectedText(): String? {
    val currentSelection = selection ?: return null
    return selectionTextPrefix + currentSelection.text + selectionTextSuffix
  }
}

internal fun Float.selectionPageTurnDirection(
  height: Int,
  density: Float,
): SelectionPageTurnDirection? {
  if (height <= 0) return null
  val edgeHeightPx = SELECTION_PAGE_TURN_EDGE_DP * density.coerceAtLeast(1f)
  return when {
    this <= edgeHeightPx -> SelectionPageTurnDirection.Previous
    this >= height - edgeHeightPx -> SelectionPageTurnDirection.Next
    else -> null
  }
}
