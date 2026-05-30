package com.charliesbot.kanshu.navigator.engine

import android.text.StaticLayout
import com.charliesbot.kanshu.navigator.model.HorizontalRule
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

    val measuredBlocks = mutableListOf<MeasuredBlock>()
    document.blocks.forEachIndexed { index, block ->
      if (!shouldContinue()) return emptyList()
      val style = styleResolver(block) ?: return@forEachIndexed
      if (block is HorizontalRule) {
        measuredBlocks.add(
          MeasuredBlock.Rule(
            blockIndex = index,
            style = style,
            heightPx = viewport.density.coerceAtLeast(1f),
          )
        )
        return@forEachIndexed
      }
      val text = SpanFlattener.flatten(block) ?: return@forEachIndexed
      if (text.isBlank()) return@forEachIndexed
      val layout = StaticLayoutFactory.build(text, style, contentWidthPx, justify)
      measuredBlocks.add(MeasuredBlock.Text(index, style, layout))
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

    fun addFullBlock(measured: MeasuredBlock.Text, yOffset: Float) {
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
      measured: MeasuredBlock.Text,
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

    fun addRule(measured: MeasuredBlock.Rule, yOffset: Float) {
      currentEntries.add(
        PageEntry.HorizontalRule(
          blockIndex = measured.blockIndex,
          yOffsetPx = yOffset,
          visibleHeightPx = measured.heightPx,
          drawOffsetXPx = drawOffsetX(measured.style),
        )
      )
      yCursor = yOffset + measured.heightPx
      previousMarginBottom = measured.style.marginBottomPx
    }

    fun firstLineHeight(layout: StaticLayout, lineStart: Int): Float =
      layout.getLineBottom(lineStart).toFloat() - layout.getLineTop(lineStart).toFloat()

    fun lastLineThatFits(layout: StaticLayout, lineStart: Int, availableHeight: Float): Int {
      var lineEnd = lineStart
      val firstLineTop = layout.getLineTop(lineStart).toFloat()
      while (lineEnd < layout.lineCount - 1) {
        val height = layout.getLineBottom(lineEnd + 1).toFloat() - firstLineTop
        if (height > availableHeight) break
        lineEnd++
      }
      return lineEnd
    }

    fun canFitAnyLine(layout: StaticLayout, lineStart: Int, availableHeight: Float): Boolean {
      if (availableHeight <= 0f) return false
      return firstLineHeight(layout, lineStart) <= availableHeight
    }

    fun addSplitBlockAcrossPages(measured: MeasuredBlock.Text, firstPageYOffset: Float = 0f) {
      val layout = measured.layout
      var lineStart = 0
      var yOffset = firstPageYOffset
      while (lineStart < layout.lineCount) {
        val availableHeight = contentHeightPx - yOffset
        if (!canFitAnyLine(layout, lineStart, availableHeight) && currentEntries.isNotEmpty()) {
          flushPage()
          yOffset = 0f
          continue
        }

        val lineEnd = lastLineThatFits(layout, lineStart, availableHeight)
        val firstLineTop = layout.getLineTop(lineStart).toFloat()
        val visibleHeight = layout.getLineBottom(lineEnd).toFloat() - firstLineTop
        addSplitBlock(measured, yOffset, lineStart..lineEnd, visibleHeight, firstLineTop)
        lineStart = lineEnd + 1
        if (lineStart < layout.lineCount) {
          flushPage()
          yOffset = 0f
        }
      }
    }

    fun addOverflowingBlock(measured: MeasuredBlock.Text, blockY: Float, remainingHeight: Float) {
      if (
        currentEntries.isNotEmpty() &&
          canFitAnyLine(measured.layout, lineStart = 0, remainingHeight)
      ) {
        addSplitBlockAcrossPages(measured, blockY)
        return
      }

      if (currentEntries.isNotEmpty()) {
        flushPage()
      }

      if (measured.layout.height.toFloat() <= contentHeightPx) {
        addFullBlock(measured, 0f)
      } else {
        addSplitBlockAcrossPages(measured)
      }
    }

    fun addMeasuredBlock(measured: MeasuredBlock) {
      val style = measured.style
      val blockHeight =
        when (measured) {
          is MeasuredBlock.Text -> measured.layout.height.toFloat()
          is MeasuredBlock.Rule -> measured.heightPx
        }
      val topMargin =
        if (currentEntries.isEmpty()) 0f else max(previousMarginBottom, style.marginTopPx)
      val blockY = yCursor + topMargin
      val remainingHeight = contentHeightPx - blockY

      when (measured) {
        is MeasuredBlock.Rule -> {
          if (blockHeight > remainingHeight && currentEntries.isNotEmpty()) {
            flushPage()
            addRule(measured, 0f)
          } else {
            addRule(measured, blockY)
          }
        }
        is MeasuredBlock.Text -> {
          if (blockHeight <= remainingHeight) {
            addFullBlock(measured, blockY)
          } else {
            addOverflowingBlock(measured, blockY, remainingHeight)
          }
        }
      }
    }

    measuredBlocks.forEach { measured ->
      if (!shouldContinue()) return emptyList()
      addMeasuredBlock(measured)
    }

    flushPage()
    return pages.ifEmpty { listOf(ReaderPage(emptyList())) }
  }
}

private sealed interface MeasuredBlock {
  val blockIndex: Int
  val style: BlockStyle

  data class Text(
    override val blockIndex: Int,
    override val style: BlockStyle,
    val layout: StaticLayout,
  ) : MeasuredBlock

  data class Rule(
    override val blockIndex: Int,
    override val style: BlockStyle,
    val heightPx: Float,
  ) : MeasuredBlock
}
