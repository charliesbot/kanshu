package com.charliesbot.kanshu.navigator.engine

import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.ReaderBlock

internal class BlockStyleResolver(
  private val preferences: ReaderPreferences,
  private val typeface: Typeface,
  private val density: Float,
) {
  private val fontSizePx = BASE_TEXT_SP * preferences.fontScale * density

  fun resolve(block: ReaderBlock): BlockStyle? =
    when (block) {
      is ParagraphBlock -> paragraphStyle()
      else -> null
    }

  private fun paragraphStyle(): BlockStyle {
    val paint =
      TextPaint().apply {
        this.typeface = typeface
        textSize = fontSizePx
        color = Color.BLACK
        isAntiAlias = true
        letterSpacing = preferences.letterSpacing
      }

    return BlockStyle(
      paint = paint,
      lineSpacingMultiplier = preferences.lineSpacing,
      lineSpacingAdd = 0f,
      hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL,
      alignment = Layout.Alignment.ALIGN_NORMAL,
      breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY,
      indentPx = 0f,
      prefixWidthPx = 0f,
      marginTopPx = 0f,
      marginBottomPx = preferences.paragraphSpacing * fontSizePx,
    )
  }

  fun horizontalMarginPx(): Float = preferences.margins.value.toFloat() * BASE_MARGIN_DP * density

  fun verticalMarginPx(): Float = preferences.margins.value.toFloat() * BASE_MARGIN_DP * density

  fun justifyText(): Boolean = preferences.alignment == ReaderAlignment.Justify

  private companion object {
    const val BASE_TEXT_SP = 18f
    const val BASE_MARGIN_DP = 24f
  }
}
