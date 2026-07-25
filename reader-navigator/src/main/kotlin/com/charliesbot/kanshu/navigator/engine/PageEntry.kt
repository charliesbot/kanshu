package com.charliesbot.kanshu.navigator.engine

import android.text.StaticLayout

sealed interface PageEntry {
  val blockIndex: Int
  val selectionId: Int
    get() = blockIndex

  val yOffsetPx: Float
  val visibleHeightPx: Float
  val drawOffsetXPx: Float
  val textJustified: Boolean
    get() = false

  /**
   * Offset of this entry's first character into the chapter's flattened text stream — the
   * denominator-free half of [com.charliesbot.kanshu.navigator.engine.ReaderLayoutResult]'s
   * progress primitive. Rules and images carry the stream position they sit at without consuming
   * any of it, so every page resolves to an offset even when it holds no text.
   *
   * Deliberately abstract: a new entry type that inherited a 0 default would silently report the
   * chapter start and send resume to the wrong page.
   */
  val textStartCharOffset: Int

  data class FullBlock(
    override val blockIndex: Int,
    override val selectionId: Int = blockIndex,
    override val yOffsetPx: Float,
    override val visibleHeightPx: Float,
    override val drawOffsetXPx: Float,
    override val textJustified: Boolean = false,
    override val textStartCharOffset: Int = 0,
    val leadingRuleOffsetXPx: Float = 0f,
    val leadingRuleStrokeWidthPx: Float = 0f,
    val markerText: String? = null,
    val markerOffsetXPx: Float = 0f,
    val layout: StaticLayout,
  ) : PageEntry

  data class SplitBlock(
    override val blockIndex: Int,
    override val selectionId: Int = blockIndex,
    override val yOffsetPx: Float,
    override val visibleHeightPx: Float,
    override val drawOffsetXPx: Float,
    override val textJustified: Boolean = false,
    override val textStartCharOffset: Int = 0,
    val leadingRuleOffsetXPx: Float = 0f,
    val leadingRuleStrokeWidthPx: Float = 0f,
    val markerText: String? = null,
    val markerOffsetXPx: Float = 0f,
    val layout: StaticLayout,
    val lineRange: IntRange,
    val firstLineTopPx: Float,
  ) : PageEntry

  data class HorizontalRule(
    override val blockIndex: Int,
    override val yOffsetPx: Float,
    override val visibleHeightPx: Float,
    override val drawOffsetXPx: Float,
    override val textStartCharOffset: Int = 0,
  ) : PageEntry

  data class Image(
    override val blockIndex: Int,
    override val yOffsetPx: Float,
    override val visibleHeightPx: Float,
    override val drawOffsetXPx: Float,
    val resourceHref: String,
    val alt: String?,
    val widthPx: Float,
    override val textStartCharOffset: Int = 0,
  ) : PageEntry
}

data class ReaderPage(val entries: List<PageEntry>) {
  /**
   * Offset of the page's first character into the chapter's text stream. Split blocks resolve to
   * the first *visible* line's offset rather than the block's, so resuming mid-paragraph returns to
   * the page the reader actually left. Empty pages report 0.
   */
  val startCharOffset: Int
    get() {
      val entry = entries.firstOrNull() ?: return 0
      return when (entry) {
        is PageEntry.SplitBlock ->
          entry.textStartCharOffset + entry.layout.getLineStart(entry.lineRange.first)
        else -> entry.textStartCharOffset
      }
    }
}

/**
 * Pagination output. [textStreamLength] is the chapter's total flattened text length — the
 * denominator for `progressInSpine`. It counts every character the parser produced, including
 * blocks that were blank enough to skip rendering, so offsets stay stable regardless of what the
 * renderer chose to draw. List markers and image placeholders contribute nothing, matching the
 * progress model in `docs/PRD_NATIVE_READER.md`.
 */
data class ReaderLayoutResult(val pages: List<ReaderPage>, val textStreamLength: Int)
