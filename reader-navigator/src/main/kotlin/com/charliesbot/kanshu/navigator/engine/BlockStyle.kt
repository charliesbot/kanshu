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
)
