package com.charliesbot.kanshu.navigator.engine

import android.text.StaticLayout
import com.charliesbot.kanshu.navigator.model.HorizontalRule
import com.charliesbot.kanshu.navigator.model.ImageBlock
import com.charliesbot.kanshu.navigator.model.ListBlock
import com.charliesbot.kanshu.navigator.model.ListItem
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
    imageBounds: (String) -> ImageBounds? = { null },
    shouldContinue: () -> Boolean = { true },
  ): List<ReaderPage> {
    val contentWidthPx = (viewport.widthPx - horizontalMarginPx * 2).toInt().coerceAtLeast(1)
    val contentHeightPx = (viewport.heightPx - verticalMarginPx * 2).coerceAtLeast(1f)

    val measuredBlocks =
      measureBlocks(
        document = document,
        viewport = viewport,
        contentWidthPx = contentWidthPx,
        contentHeightPx = contentHeightPx,
        justify = justify,
        styleResolver = styleResolver,
        imageBounds = imageBounds,
        shouldContinue = shouldContinue,
      ) ?: return emptyList()

    if (measuredBlocks.isEmpty()) {
      return listOf(ReaderPage(emptyList()))
    }

    val paginator = Paginator(contentWidthPx, contentHeightPx)
    measuredBlocks.forEach { measured ->
      if (!shouldContinue()) return emptyList()
      paginator.add(measured)
    }
    return paginator.build()
  }

  /** Measures every block into a page-agnostic [MeasuredBlock], or null when aborted. */
  private fun measureBlocks(
    document: ReaderDocument,
    viewport: ReaderViewport,
    contentWidthPx: Int,
    contentHeightPx: Float,
    justify: Boolean,
    styleResolver: (ReaderBlock) -> BlockStyle?,
    imageBounds: (String) -> ImageBounds?,
    shouldContinue: () -> Boolean,
  ): List<MeasuredBlock>? {
    val measuredBlocks = mutableListOf<MeasuredBlock>()
    var nextSyntheticSelectionId = document.blocks.size
    fun syntheticSelectionId(): Int = nextSyntheticSelectionId++

    document.blocks.forEachIndexed { index, block ->
      if (!shouldContinue()) return null
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
      if (block is ImageBlock) {
        measuredBlocks.add(
          measureImageBlock(
            blockIndex = index,
            block = block,
            style = style,
            contentWidthPx = contentWidthPx,
            contentHeightPx = contentHeightPx,
            bounds = imageBounds(block.resourceHref),
          )
        )
        return@forEachIndexed
      }
      if (block is ListBlock) {
        appendListBlock(
          blockIndex = index,
          block = block,
          style = style,
          depth = 0,
          contentWidthPx = contentWidthPx,
          justify = justify,
          selectionId = ::syntheticSelectionId,
          measuredBlocks = measuredBlocks,
        )
        return@forEachIndexed
      }
      val text = SpanFlattener.flatten(block) ?: return@forEachIndexed
      if (text.isBlank()) return@forEachIndexed
      val layout = StaticLayoutFactory.build(text, style, contentWidthPx, justify)
      measuredBlocks.add(
        MeasuredBlock.Text(
          blockIndex = index,
          selectionId = index,
          style = style,
          layout = layout,
          textJustified = justify && style.justifiable,
        )
      )
    }
    return measuredBlocks
  }
}

/**
 * Flows measured blocks into pages: tracks the y cursor and collapsed vertical margins on the
 * current page, splits text blocks across page boundaries, and keeps rules and images atomic.
 */
private class Paginator(private val contentWidthPx: Int, private val contentHeightPx: Float) {
  private val pages = mutableListOf<ReaderPage>()
  private var currentEntries = mutableListOf<PageEntry>()
  private var yCursor = 0f
  private var previousMarginBottom = 0f

  fun add(measured: MeasuredBlock) {
    val topMargin =
      if (currentEntries.isEmpty()) 0f else max(previousMarginBottom, measured.style.marginTopPx)
    val blockY = yCursor + topMargin
    val remainingHeight = contentHeightPx - blockY

    when (measured) {
      is MeasuredBlock.Image -> {
        if (measured.heightPx > remainingHeight && currentEntries.isNotEmpty()) {
          flushPage()
          addImage(measured, 0f)
        } else {
          addImage(measured, blockY)
        }
      }
      is MeasuredBlock.Rule -> {
        if (measured.heightPx > remainingHeight && currentEntries.isNotEmpty()) {
          flushPage()
          addRule(measured, 0f)
        } else {
          addRule(measured, blockY)
        }
      }
      is MeasuredBlock.Text -> {
        if (measured.heightPx <= remainingHeight) {
          addFullBlock(measured, blockY)
        } else {
          addOverflowingBlock(measured, blockY, remainingHeight)
        }
      }
    }
  }

  fun build(): List<ReaderPage> {
    flushPage()
    return pages.ifEmpty { listOf(ReaderPage(emptyList())) }
  }

  private fun flushPage() {
    if (currentEntries.isNotEmpty()) {
      pages.add(ReaderPage(currentEntries.toList()))
      currentEntries = mutableListOf()
    }
    yCursor = 0f
    previousMarginBottom = 0f
  }

  /** Appends the entry and advances the cursor past it, remembering its bottom margin. */
  private fun append(entry: PageEntry, marginBottomPx: Float) {
    currentEntries.add(entry)
    yCursor = entry.yOffsetPx + entry.visibleHeightPx
    previousMarginBottom = marginBottomPx
  }

  private fun depthOffsetX(style: BlockStyle, depth: Int): Float = depth * style.prefixWidthPx

  private fun drawOffsetX(measured: MeasuredBlock.Text): Float =
    measured.style.indentPx +
      measured.style.prefixWidthPx +
      depthOffsetX(measured.style, measured.depth)

  private fun drawOffsetX(style: BlockStyle): Float = style.indentPx + style.prefixWidthPx

  private fun addFullBlock(measured: MeasuredBlock.Text, yOffset: Float) {
    append(
      PageEntry.FullBlock(
        blockIndex = measured.blockIndex,
        selectionId = measured.selectionId,
        yOffsetPx = yOffset,
        visibleHeightPx = measured.layout.height.toFloat(),
        drawOffsetXPx = drawOffsetX(measured),
        textJustified = measured.textJustified,
        leadingRuleOffsetXPx = measured.style.leadingRuleOffsetXPx,
        leadingRuleStrokeWidthPx = measured.style.leadingRuleStrokeWidthPx,
        markerText = measured.markerText,
        markerOffsetXPx = depthOffsetX(measured.style, measured.depth),
        layout = measured.layout,
      ),
      marginBottomPx = measured.style.marginBottomPx,
    )
  }

  private fun addSplitBlock(
    measured: MeasuredBlock.Text,
    yOffset: Float,
    lineRange: IntRange,
    visibleHeight: Float,
    firstLineTop: Float,
  ) {
    append(
      PageEntry.SplitBlock(
        blockIndex = measured.blockIndex,
        selectionId = measured.selectionId,
        yOffsetPx = yOffset,
        visibleHeightPx = visibleHeight,
        drawOffsetXPx = drawOffsetX(measured),
        textJustified = measured.textJustified,
        leadingRuleOffsetXPx = measured.style.leadingRuleOffsetXPx,
        leadingRuleStrokeWidthPx = measured.style.leadingRuleStrokeWidthPx,
        markerText = if (lineRange.first == 0) measured.markerText else null,
        markerOffsetXPx = depthOffsetX(measured.style, measured.depth),
        layout = measured.layout,
        lineRange = lineRange,
        firstLineTopPx = firstLineTop,
      ),
      marginBottomPx = measured.style.marginBottomPx,
    )
  }

  private fun addRule(measured: MeasuredBlock.Rule, yOffset: Float) {
    append(
      PageEntry.HorizontalRule(
        blockIndex = measured.blockIndex,
        yOffsetPx = yOffset,
        visibleHeightPx = measured.heightPx,
        drawOffsetXPx = drawOffsetX(measured.style),
      ),
      marginBottomPx = measured.style.marginBottomPx,
    )
  }

  private fun addImage(measured: MeasuredBlock.Image, yOffset: Float) {
    val centerOffsetPx = ((contentWidthPx - measured.widthPx) / 2f).coerceAtLeast(0f)
    append(
      PageEntry.Image(
        blockIndex = measured.blockIndex,
        yOffsetPx = yOffset,
        visibleHeightPx = measured.heightPx,
        drawOffsetXPx = drawOffsetX(measured.style) + centerOffsetPx,
        resourceHref = measured.resourceHref,
        alt = measured.alt,
        widthPx = measured.widthPx,
      ),
      marginBottomPx = measured.style.marginBottomPx,
    )
  }

  private fun firstLineHeight(layout: StaticLayout, lineStart: Int): Float =
    layout.getLineBottom(lineStart).toFloat() - layout.getLineTop(lineStart).toFloat()

  private fun lastLineThatFits(layout: StaticLayout, lineStart: Int, availableHeight: Float): Int {
    var lineEnd = lineStart
    val firstLineTop = layout.getLineTop(lineStart).toFloat()
    while (lineEnd < layout.lineCount - 1) {
      val height = layout.getLineBottom(lineEnd + 1).toFloat() - firstLineTop
      if (height > availableHeight) break
      lineEnd++
    }
    return lineEnd
  }

  private fun canFitAnyLine(layout: StaticLayout, lineStart: Int, availableHeight: Float): Boolean {
    if (availableHeight <= 0f) return false
    return firstLineHeight(layout, lineStart) <= availableHeight
  }

  private fun addSplitBlockAcrossPages(measured: MeasuredBlock.Text, firstPageYOffset: Float = 0f) {
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

  private fun addOverflowingBlock(
    measured: MeasuredBlock.Text,
    blockY: Float,
    remainingHeight: Float,
  ) {
    if (
      currentEntries.isNotEmpty() && canFitAnyLine(measured.layout, lineStart = 0, remainingHeight)
    ) {
      addSplitBlockAcrossPages(measured, blockY)
      return
    }

    if (currentEntries.isNotEmpty()) {
      flushPage()
    }

    if (measured.heightPx <= contentHeightPx) {
      addFullBlock(measured, 0f)
    } else {
      addSplitBlockAcrossPages(measured)
    }
  }
}

private fun appendListBlock(
  blockIndex: Int,
  block: ListBlock,
  style: BlockStyle,
  depth: Int,
  contentWidthPx: Int,
  justify: Boolean,
  selectionId: () -> Int,
  measuredBlocks: MutableList<MeasuredBlock>,
) {
  block.items.forEachIndexed { itemIndex, item ->
    val textBlocks = mutableListOf<ReaderBlock>()
    var emittedItemText = false

    fun flushTextBlocks() {
      val text = SpanFlattener.flatten(ListItem(textBlocks))
      if (!text.isNullOrBlank()) {
        measuredBlocks.add(
          MeasuredBlock.Text(
            blockIndex = blockIndex,
            selectionId = selectionId(),
            style = style,
            layout =
              StaticLayoutFactory.build(text, style.withListDepth(depth), contentWidthPx, justify),
            textJustified = justify,
            markerText =
              if (emittedItemText) null
              else if (block.ordered) "${itemIndex + 1}." else BULLET_MARKER,
            depth = depth,
          )
        )
        emittedItemText = true
      }
      textBlocks.clear()
    }

    item.blocks.forEach { child ->
      if (child is ListBlock) {
        flushTextBlocks()
        appendListBlock(
          blockIndex = blockIndex,
          block = child,
          style = style,
          depth = depth + 1,
          contentWidthPx = contentWidthPx,
          justify = justify,
          selectionId = selectionId,
          measuredBlocks = measuredBlocks,
        )
      } else {
        textBlocks.add(child)
      }
    }

    flushTextBlocks()
  }
}

private fun BlockStyle.withListDepth(depth: Int): BlockStyle =
  copy(indentPx = indentPx + prefixWidthPx * depth)

private sealed interface MeasuredBlock {
  val blockIndex: Int
  val style: BlockStyle
  val heightPx: Float

  data class Text(
    override val blockIndex: Int,
    val selectionId: Int,
    override val style: BlockStyle,
    val layout: StaticLayout,
    val textJustified: Boolean,
    val markerText: String? = null,
    val depth: Int = 0,
  ) : MeasuredBlock {
    override val heightPx: Float
      get() = layout.height.toFloat()
  }

  data class Rule(
    override val blockIndex: Int,
    override val style: BlockStyle,
    override val heightPx: Float,
  ) : MeasuredBlock

  data class Image(
    override val blockIndex: Int,
    override val style: BlockStyle,
    val resourceHref: String,
    val alt: String?,
    val widthPx: Float,
    override val heightPx: Float,
  ) : MeasuredBlock
}

private fun measureImageBlock(
  blockIndex: Int,
  block: ImageBlock,
  style: BlockStyle,
  contentWidthPx: Int,
  contentHeightPx: Float,
  bounds: ImageBounds?,
): MeasuredBlock.Image {
  val (widthPx, heightPx) =
    if (bounds != null && bounds.intrinsicWidthPx > 0 && bounds.intrinsicHeightPx > 0) {
      val widthScale = (contentWidthPx / bounds.intrinsicWidthPx.toFloat()).coerceAtMost(1f)
      var widthPx = bounds.intrinsicWidthPx * widthScale
      var heightPx = bounds.intrinsicHeightPx * widthScale
      if (heightPx > contentHeightPx) {
        widthPx *= contentHeightPx / heightPx
        heightPx = contentHeightPx
      }
      widthPx to heightPx
    } else {
      contentWidthPx.toFloat() to placeholderImageHeightPx(style)
    }
  return MeasuredBlock.Image(
    blockIndex = blockIndex,
    style = style,
    resourceHref = block.resourceHref,
    alt = block.alt,
    widthPx = widthPx,
    heightPx = heightPx,
  )
}

private fun placeholderImageHeightPx(style: BlockStyle): Float =
  (style.paint.fontMetrics.bottom - style.paint.fontMetrics.top)
    .coerceAtLeast(style.paint.textSize)
    .coerceAtLeast(1f)

private const val BULLET_MARKER = "\u2022"
