package com.charliesbot.kanshu.navigator.engine

import android.text.StaticLayout
import com.charliesbot.kanshu.navigator.model.ReaderBlock
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import kotlin.math.max

class ReaderLayoutEngine {
  fun layout(
    document: ReaderDocument,
    viewport: ReaderViewport,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
    justify: Boolean,
    styleResolver: (ReaderBlock) -> BlockStyle?,
    shouldContinue: () -> Boolean = { true },
  ): List<ReaderPage> {
    val contentWidthPx = (viewport.widthPx - horizontalMarginPx * 2).toInt().coerceAtLeast(1)
    val contentHeightPx = (viewport.heightPx - verticalMarginPx * 2).coerceAtLeast(1f)

    data class MeasuredBlock(val blockIndex: Int, val style: BlockStyle, val layout: StaticLayout)

    val measuredBlocks = mutableListOf<MeasuredBlock>()
    document.blocks.forEachIndexed { index, block ->
      if (!shouldContinue()) return emptyList()
      val style = styleResolver(block) ?: return@forEachIndexed
      val text = SpanFlattener.flatten(block) ?: return@forEachIndexed
      if (text.isBlank()) return@forEachIndexed
      val layout = StaticLayoutFactory.build(text, style, contentWidthPx, justify)
      measuredBlocks.add(MeasuredBlock(index, style, layout))
    }

    if (measuredBlocks.isEmpty()) {
      return listOf(ReaderPage(emptyList()))
    }

    val pages = mutableListOf<ReaderPage>()
    var currentEntries = mutableListOf<PageEntry>()
    var yCursor = 0f
    var previousMarginBottom = 0f

    fun flushPage() {
      if (currentEntries.isNotEmpty()) {
        pages.add(ReaderPage(currentEntries.toList()))
        currentEntries = mutableListOf()
      }
      yCursor = 0f
      previousMarginBottom = 0f
    }

    fun drawOffsetX(style: BlockStyle): Float = style.indentPx + style.prefixWidthPx

    fun estimatedLineHeightPx(measured: MeasuredBlock): Float {
      if (measured.layout.lineCount == 0) return 0f
      return measured.layout.getLineBottom(0) - measured.layout.getLineTop(0).toFloat()
    }

    fun fitsWithOrphanSlack(measured: MeasuredBlock, remainingHeight: Float): Boolean {
      val layoutHeight = measured.layout.height.toFloat()
      return layoutHeight <= remainingHeight + estimatedLineHeightPx(measured)
    }

    fun addFullBlock(measured: MeasuredBlock, yOffset: Float) {
      currentEntries.add(
        PageEntry.FullBlock(
          blockIndex = measured.blockIndex,
          yOffsetPx = yOffset,
          visibleHeightPx = measured.layout.height.toFloat(),
          drawOffsetXPx = drawOffsetX(measured.style),
          layout = measured.layout,
        )
      )
      yCursor = yOffset + measured.layout.height
      previousMarginBottom = measured.style.marginBottomPx
    }

    fun addSplitBlock(
      measured: MeasuredBlock,
      yOffset: Float,
      lineRange: IntRange,
      visibleHeight: Float,
      firstLineTop: Float,
    ) {
      currentEntries.add(
        PageEntry.SplitBlock(
          blockIndex = measured.blockIndex,
          yOffsetPx = yOffset,
          visibleHeightPx = visibleHeight,
          drawOffsetXPx = drawOffsetX(measured.style),
          layout = measured.layout,
          lineRange = lineRange,
          firstLineTopPx = firstLineTop,
        )
      )
      yCursor = yOffset + visibleHeight
      previousMarginBottom = measured.style.marginBottomPx
    }

    fun splitBlock(measured: MeasuredBlock) {
      val layout = measured.layout
      var lineStart = 0
      while (lineStart < layout.lineCount) {
        if (currentEntries.isNotEmpty()) {
          flushPage()
        }

        val remainingHeight = contentHeightPx - yCursor

        var lineEnd = lineStart
        while (lineEnd < layout.lineCount - 1) {
          val height =
            layout.getLineBottom(lineEnd + 1).toFloat() - layout.getLineTop(lineStart).toFloat()
          if (height > remainingHeight) break
          lineEnd++
        }

        val firstLineTop = layout.getLineTop(lineStart).toFloat()
        val visibleHeight = layout.getLineBottom(lineEnd).toFloat() - firstLineTop
        addSplitBlock(measured, yCursor, lineStart..lineEnd, visibleHeight, firstLineTop)
        lineStart = lineEnd + 1
      }
    }

    measuredBlocks.forEach { measured ->
      if (!shouldContinue()) return emptyList()

      val style = measured.style
      val layoutHeight = measured.layout.height.toFloat()
      val topMargin =
        if (currentEntries.isEmpty()) 0f else max(previousMarginBottom, style.marginTopPx)
      val blockY = yCursor + topMargin
      val remainingHeight = contentHeightPx - blockY

      if (layoutHeight <= remainingHeight || fitsWithOrphanSlack(measured, remainingHeight)) {
        addFullBlock(measured, blockY)
        return@forEach
      }

      if (currentEntries.isNotEmpty()) {
        flushPage()
        val retryRemaining = contentHeightPx
        if (layoutHeight <= retryRemaining) {
          addFullBlock(measured, 0f)
          return@forEach
        }
        splitBlock(measured)
        return@forEach
      }

      splitBlock(measured)
    }

    flushPage()
    return pages.ifEmpty { listOf(ReaderPage(emptyList())) }
  }
}
