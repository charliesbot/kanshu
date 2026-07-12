package com.charliesbot.kanshu.navigator.engine

import android.text.Layout
import android.text.TextPaint

data class BlockStyle(
  val paint: TextPaint,
  val lineSpacingMultiplier: Float,
  val lineSpacingAdd: Float,
  val hyphenationFrequency: Int,
  val alignment: Layout.Alignment,
  val breakStrategy: Int,
  val indentPx: Float,
  val prefixWidthPx: Float,
  val marginTopPx: Float,
  val marginBottomPx: Float,
  val leadingRuleOffsetXPx: Float = 0f,
  val leadingRuleStrokeWidthPx: Float = 0f,
  /** False when the publisher declared an explicit alignment — such blocks never justify. */
  val justifiable: Boolean = true,
)
