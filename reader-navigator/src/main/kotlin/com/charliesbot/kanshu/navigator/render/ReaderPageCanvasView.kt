package com.charliesbot.kanshu.navigator.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.charliesbot.kanshu.navigator.ReaderHighlight
import com.charliesbot.kanshu.navigator.ReaderHighlightTap
import com.charliesbot.kanshu.navigator.ReaderSelectionInfo
import com.charliesbot.kanshu.navigator.engine.ReaderPage
import com.charliesbot.kanshu.navigator.selection.ReaderSelector
import com.charliesbot.kanshu.navigator.selection.TextSelection
import java.util.Locale

internal class ReaderPageCanvasView(context: Context) : View(context) {
  private var page: ReaderPage? = null
  private var highlights: List<ReaderHighlight> = emptyList()
  private var clearSelectionToken = 0
  private var horizontalMarginPx = 0f
  private var verticalMarginPx = 0f
  private var imageBitmaps: Map<String, Bitmap> = emptyMap()
  private var onTapZone: ((ReaderPageTapZone) -> Unit)? = null
  private var onLinkTapped: ((String) -> Unit)? = null
  private var onHighlightTapped: ((ReaderHighlightTap) -> Unit)? = null
  private var handledLongPress = false
  private var pendingClickZone = ReaderPageTapZone.Center
  private val selectionController =
    ReaderPageSelectionController(
      scheduler = SelectionPageTurnScheduler(),
      viewportHeightPx = { height },
      density = { resources.displayMetrics.density },
      invalidate = ::invalidate,
    )
  private val gestureDetector =
    GestureDetector(
      context,
      object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean {
          handledLongPress = false
          return true
        }

        override fun onSingleTapUp(event: MotionEvent): Boolean {
          if (handledLongPress) {
            handledLongPress = false
            return true
          }
          if (selectionController.clearSelection()) {
            return true
          }
          if (dispatchHighlightTap(event.x, event.y)) {
            return true
          }
          if (dispatchLinkTap(event.x, event.y)) {
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
    imageBitmaps: Map<String, Bitmap> = emptyMap(),
    highlights: List<ReaderHighlight> = emptyList(),
    clearSelectionToken: Int = 0,
    onTapZone: ((ReaderPageTapZone) -> Unit)? = null,
    onLinkTapped: ((String) -> Unit)? = null,
    onHighlightTapped: ((ReaderHighlightTap) -> Unit)? = null,
    onTextSelected: ((ReaderSelectionInfo) -> Unit)? = null,
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
    this.page = page
    this.horizontalMarginPx = horizontalMarginPx
    this.verticalMarginPx = verticalMarginPx
    this.imageBitmaps = imageBitmaps
    this.highlights = highlights
    this.onTapZone = onTapZone
    this.onLinkTapped = onLinkTapped
    this.onHighlightTapped = onHighlightTapped
    if (
      selectionController.setPage(
        page = page,
        horizontalMarginPx = horizontalMarginPx,
        verticalMarginPx = verticalMarginPx,
        onTextSelected = onTextSelected,
        onSelectionCleared = onSelectionCleared,
        onSelectionPageTurn = onSelectionPageTurn,
        selectionTextPrefix = selectionTextPrefix,
        selectionTextSuffix = selectionTextSuffix,
        selectionLocale = selectionLocale,
        restoredSelection = restoredSelection,
        seedSelectionAtPageStart = seedSelectionAtPageStart,
        seedSelectionAtPageEnd = seedSelectionAtPageEnd,
      )
    ) {
      handledLongPress = true
    }
    // Committing an action on the selection (e.g. highlighting it) has to drop the selection
    // itself; otherwise the next recomposition re-reports it and the popup reopens over it.
    if (clearSelectionToken != this.clearSelectionToken) {
      this.clearSelectionToken = clearSelectionToken
      selectionController.clearSelection()
    }
    Log.d(TAG, "render entries=${page.entries.size}")
    invalidate()
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val handledByDetector = gestureDetector.onTouchEvent(event)
    when (event.actionMasked) {
      MotionEvent.ACTION_MOVE -> {
        if (handledLongPress) {
          selectionController.updateSelection(event.x, event.y)
          return true
        }
      }

      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        if (handledLongPress) {
          handledLongPress = false
          selectionController.finishDrag()
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
    selectionController.release()
    onTapZone = null
    onLinkTapped = null
    onHighlightTapped = null
  }

  /** A tap on link text consumes the gesture; a miss falls through to the tap zones. */
  private fun dispatchLinkTap(xPx: Float, yPx: Float): Boolean {
    val currentPage = page ?: return false
    val callback = onLinkTapped ?: return false
    val href =
      ReaderSelector.linkHrefAt(currentPage, xPx, yPx, horizontalMarginPx, verticalMarginPx)
        ?: return false
    callback(href)
    return true
  }

  /** A tap on highlighted text consumes the gesture before links and page tap zones. */
  private fun dispatchHighlightTap(xPx: Float, yPx: Float): Boolean {
    val currentPage = page ?: return false
    val callback = onHighlightTapped ?: return false
    val hit =
      ReaderSelector.highlightAt(
        page = currentPage,
        highlights = highlights,
        xPx = xPx,
        yPx = yPx,
        horizontalMarginPx = horizontalMarginPx,
        verticalMarginPx = verticalMarginPx,
      ) ?: return false
    callback(hit)
    return true
  }

  override fun performClick(): Boolean {
    super.performClick()
    onTapZone?.invoke(pendingClickZone)
    pendingClickZone = ReaderPageTapZone.Center
    return true
  }

  internal fun startSelectionFromLongPress(xPx: Float, yPx: Float) {
    if (page == null) return
    handledLongPress = true
    selectionController.startSelectionFromLongPress(xPx, yPx)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val page = page ?: return
    PageRenderer.draw(
      canvas = canvas,
      page = page,
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
      selectionRects = selectionController.selectionRects,
      highlights =
        ReaderSelector.renderedHighlights(
          page = page,
          highlights = highlights,
          horizontalMarginPx = horizontalMarginPx,
          verticalMarginPx = verticalMarginPx,
        ),
      imageBitmaps = imageBitmaps,
    )
  }

  private companion object {
    const val TAG = "ReaderPageCanvasView"
  }
}
