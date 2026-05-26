package com.charliesbot.kanshu.navigator.render

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.view.View
import com.charliesbot.kanshu.navigator.engine.PageEntry
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
    logPage(page)
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val page = page ?: return
    PageRenderer.draw(canvas, page, horizontalMarginPx, verticalMarginPx)
  }

  private fun logPage(page: ReaderPage) {
    val summaries = page.entries.map(::entryLogSummary)
    Log.d(TAG, "render entries=${page.entries.size}: ${summaries.joinToString()}")
  }

  private fun entryLogSummary(entry: PageEntry): String {
    val preview =
      when (entry) {
          is PageEntry.FullBlock -> entry.layout.text
          is PageEntry.SplitBlock -> entry.layout.text
        }
        .toString()
        .trim()
        .take(40)
    return when (entry) {
      is PageEntry.FullBlock ->
        "FullBlock#${entry.blockIndex} h=${entry.layout.height} lines=${entry.layout.lineCount} y=${entry.yOffsetPx} text=\"$preview\""
      is PageEntry.SplitBlock ->
        "SplitBlock#${entry.blockIndex} h=${entry.visibleHeightPx} lines=${entry.lineRange} y=${entry.yOffsetPx} text=\"$preview\""
    }
  }
}
