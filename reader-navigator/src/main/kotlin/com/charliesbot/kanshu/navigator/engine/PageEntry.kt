package com.charliesbot.kanshu.navigator.engine

import android.text.StaticLayout

sealed interface PageEntry {
  val blockIndex: Int
  val yOffsetPx: Float
  val visibleHeightPx: Float
  val drawOffsetXPx: Float

  data class FullBlock(
    override val blockIndex: Int,
    override val yOffsetPx: Float,
    override val visibleHeightPx: Float,
    override val drawOffsetXPx: Float,
    val leadingRuleOffsetXPx: Float = 0f,
    val leadingRuleStrokeWidthPx: Float = 0f,
    val layout: StaticLayout,
  ) : PageEntry

  data class SplitBlock(
    override val blockIndex: Int,
    override val yOffsetPx: Float,
    override val visibleHeightPx: Float,
    override val drawOffsetXPx: Float,
    val leadingRuleOffsetXPx: Float = 0f,
    val leadingRuleStrokeWidthPx: Float = 0f,
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
}

data class ReaderPage(val entries: List<PageEntry>)
