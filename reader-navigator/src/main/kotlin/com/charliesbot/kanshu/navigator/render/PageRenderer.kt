package com.charliesbot.kanshu.navigator.render

import android.graphics.Canvas as AndroidCanvas
import com.charliesbot.kanshu.navigator.engine.PageEntry
import com.charliesbot.kanshu.navigator.engine.ReaderPage

internal object PageRenderer {
  fun draw(
    canvas: AndroidCanvas,
    page: ReaderPage,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
  ) {
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.save()
    canvas.clipRect(
      horizontalMarginPx,
      verticalMarginPx,
      canvas.width - horizontalMarginPx,
      canvas.height - verticalMarginPx,
    )
    page.entries.forEach { entry -> drawEntry(canvas, entry, horizontalMarginPx, verticalMarginPx) }
    canvas.restore()
  }

  private fun drawEntry(
    canvas: AndroidCanvas,
    entry: PageEntry,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
  ) {
    val x = horizontalMarginPx + entry.drawOffsetXPx
    val y = verticalMarginPx + entry.yOffsetPx

    when (entry) {
      is PageEntry.FullBlock -> {
        canvas.save()
        canvas.translate(x, y)
        entry.layout.draw(canvas)
        canvas.restore()
      }

      is PageEntry.SplitBlock -> {
        val clipTop = y + 1f
        val clipBottom = y + entry.visibleHeightPx - 1f
        canvas.save()
        canvas.clipRect(horizontalMarginPx, clipTop, canvas.width.toFloat(), clipBottom)
        canvas.translate(x, y - entry.firstLineTopPx)
        entry.layout.draw(canvas)
        canvas.restore()
      }
    }
  }
}
