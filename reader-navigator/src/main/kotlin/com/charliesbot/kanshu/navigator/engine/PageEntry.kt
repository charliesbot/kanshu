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

  data class FullBlock(
    override val blockIndex: Int,
    override val selectionId: Int = blockIndex,
    override val yOffsetPx: Float,
    override val visibleHeightPx: Float,
    override val drawOffsetXPx: Float,
    override val textJustified: Boolean = false,
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
  ) : PageEntry

  data class Image(
    override val blockIndex: Int,
    override val yOffsetPx: Float,
    override val visibleHeightPx: Float,
    override val drawOffsetXPx: Float,
    val resourceHref: String,
    val alt: String?,
    val widthPx: Float,
  ) : PageEntry
}

data class ReaderPage(val entries: List<PageEntry>)
