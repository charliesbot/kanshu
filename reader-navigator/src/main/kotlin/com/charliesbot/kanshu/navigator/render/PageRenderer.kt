package com.charliesbot.kanshu.navigator.render

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.charliesbot.kanshu.navigator.engine.PageEntry
import com.charliesbot.kanshu.navigator.engine.ReaderPage

internal object PageRenderer {
  fun draw(
    canvas: AndroidCanvas,
    page: ReaderPage,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
    selectionRects: List<RectF> = emptyList(),
  ) {
    canvas.drawColor(Color.WHITE)
    canvas.save()
    canvas.clipRect(
      horizontalMarginPx,
      verticalMarginPx,
      canvas.width - horizontalMarginPx,
      canvas.height - verticalMarginPx,
    )
    selectionRects.forEach { rect -> canvas.drawRect(rect, selectionPaint) }
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
        drawLeadingRule(canvas, entry, horizontalMarginPx, y)
        drawMarker(canvas, entry.markerText, horizontalMarginPx + entry.markerOffsetXPx, y, entry)
        canvas.save()
        canvas.translate(x, y)
        entry.layout.draw(canvas)
        canvas.restore()
      }

      is PageEntry.SplitBlock -> {
        val clipTop = y + 1f
        val clipBottom = y + entry.visibleHeightPx - 1f
        drawLeadingRule(canvas, entry, horizontalMarginPx, y)
        drawMarker(canvas, entry.markerText, horizontalMarginPx + entry.markerOffsetXPx, y, entry)
        canvas.save()
        canvas.clipRect(horizontalMarginPx, clipTop, canvas.width.toFloat(), clipBottom)
        canvas.translate(x, y - entry.firstLineTopPx)
        entry.layout.draw(canvas)
        canvas.restore()
      }

      is PageEntry.HorizontalRule -> {
        val ruleY = y + entry.visibleHeightPx / 2f
        val rightX = canvas.width - horizontalMarginPx - entry.drawOffsetXPx
        rulePaint.strokeWidth = entry.visibleHeightPx.coerceAtLeast(1f)
        canvas.drawLine(x, ruleY, rightX, ruleY, rulePaint)
      }
    }
  }

  private fun drawLeadingRule(
    canvas: AndroidCanvas,
    entry: PageEntry,
    horizontalMarginPx: Float,
    y: Float,
  ) {
    val strokeWidth =
      when (entry) {
        is PageEntry.FullBlock -> entry.leadingRuleStrokeWidthPx
        is PageEntry.SplitBlock -> entry.leadingRuleStrokeWidthPx
        is PageEntry.HorizontalRule -> 0f
      }
    if (strokeWidth <= 0f) return

    val ruleOffsetX =
      when (entry) {
        is PageEntry.FullBlock -> entry.leadingRuleOffsetXPx
        is PageEntry.SplitBlock -> entry.leadingRuleOffsetXPx
        is PageEntry.HorizontalRule -> 0f
      }
    val ruleX = horizontalMarginPx + ruleOffsetX
    rulePaint.strokeWidth = strokeWidth.coerceAtLeast(1f)
    canvas.drawLine(ruleX, y, ruleX, y + entry.visibleHeightPx, rulePaint)
  }

  private fun drawMarker(
    canvas: AndroidCanvas,
    markerText: String?,
    x: Float,
    y: Float,
    entry: PageEntry,
  ) {
    if (markerText == null) return
    val baseline =
      when (entry) {
        is PageEntry.FullBlock -> y + entry.layout.getLineBaseline(0)
        is PageEntry.SplitBlock ->
          y + entry.layout.getLineBaseline(entry.lineRange.first) - entry.firstLineTopPx
        is PageEntry.HorizontalRule -> return
      }
    val paint =
      when (entry) {
        is PageEntry.FullBlock -> entry.layout.paint
        is PageEntry.SplitBlock -> entry.layout.paint
        is PageEntry.HorizontalRule -> return
      }
    canvas.drawText(markerText, x, baseline, paint)
  }

  private val rulePaint =
    Paint().apply {
      color = Color.BLACK
      isAntiAlias = false
    }

  private val selectionPaint =
    Paint().apply {
      color = Color.LTGRAY
      isAntiAlias = false
    }
}
