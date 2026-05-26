package com.charliesbot.kanshu.navigator.render

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.view.View
import com.charliesbot.kanshu.navigator.engine.ReaderPage

private const val TAG = "ReaderPageCanvasView"

internal class ReaderPageCanvasView(context: Context) : View(context) {
  private var page: ReaderPage? = null
  private var horizontalMarginPx = 0f
  private var verticalMarginPx = 0f

  fun setPage(page: ReaderPage, horizontalMarginPx: Float, verticalMarginPx: Float) {
    this.page = page
    this.horizontalMarginPx = horizontalMarginPx
    this.verticalMarginPx = verticalMarginPx
    Log.d(TAG, "render entries=${page.entries.size}")
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val page = page ?: return
    PageRenderer.draw(canvas, page, horizontalMarginPx, verticalMarginPx)
  }
}
