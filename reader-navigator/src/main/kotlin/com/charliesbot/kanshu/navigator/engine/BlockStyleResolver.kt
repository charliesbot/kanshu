package com.charliesbot.kanshu.navigator.engine

import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.navigator.model.BlockAlignment
import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.model.HorizontalRule
import com.charliesbot.kanshu.navigator.model.ImageBlock
import com.charliesbot.kanshu.navigator.model.ListBlock
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.QuoteBlock
import com.charliesbot.kanshu.navigator.model.ReaderBlock

internal class BlockStyleResolver(
  private val preferences: ReaderPreferences,
  // Named to avoid shadowing by TextPaint.typeface inside apply {} — assigning `typeface` there
  // silently self-assigns the paint's own (null) property.
  private val readerTypeface: Typeface,
  private val density: Float,
) {
  private val fontSizePx = BASE_TEXT_SP * preferences.fontScale * density

  fun resolve(block: ReaderBlock): BlockStyle? =
    when (block) {
      is HeadingBlock -> headingStyle(block.level, block.alignment)
      is HorizontalRule -> ruleStyle()
      is ImageBlock -> imageStyle()
      is ListBlock -> listStyle()
      is ParagraphBlock -> paragraphStyle(block.alignment)
      is QuoteBlock -> quoteStyle()
    }

  private fun imageStyle(): BlockStyle = paragraphStyle(publisherAlignment = null)

  /** The shared body-text paint; headings pass their own typeface and size. */
  private fun textPaint(
    paintTypeface: Typeface = readerTypeface,
    textSizePx: Float = fontSizePx,
  ): TextPaint =
    TextPaint().apply {
      typeface = paintTypeface
      textSize = textSizePx
      color = Color.BLACK
      isAntiAlias = true
      letterSpacing = preferences.letterSpacing
    }

  private fun paragraphStyle(publisherAlignment: BlockAlignment?): BlockStyle {
    return BlockStyle(
      paint = textPaint(),
      lineSpacingMultiplier = preferences.lineSpacing,
      lineSpacingAdd = 0f,
      hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL,
      alignment = publisherAlignment.toLayoutAlignment(),
      breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY,
      indentPx = 0f,
      prefixWidthPx = 0f,
      marginTopPx = 0f,
      marginBottomPx = preferences.paragraphSpacing * fontSizePx,
      justifiable = publisherAlignment == null,
    )
  }

  private fun headingStyle(level: Int, publisherAlignment: BlockAlignment?): BlockStyle {
    val headingScale =
      when (level.coerceIn(1, 6)) {
        1 -> 1.65f
        2 -> 1.45f
        3 -> 1.25f
        else -> 1.12f
      }
    val headingSizePx = fontSizePx * headingScale

    return BlockStyle(
      paint = textPaint(Typeface.create(readerTypeface, Typeface.BOLD), headingSizePx),
      lineSpacingMultiplier = preferences.lineSpacing,
      lineSpacingAdd = 0f,
      hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE,
      alignment = publisherAlignment.toLayoutAlignment(),
      breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY,
      indentPx = 0f,
      prefixWidthPx = 0f,
      marginTopPx = headingSizePx * if (level <= 3) 1.2f else 0.8f,
      marginBottomPx = headingSizePx * if (level <= 3) 0.6f else 0.4f,
      justifiable = publisherAlignment == null,
    )
  }

  private fun BlockAlignment?.toLayoutAlignment(): Layout.Alignment =
    when (this) {
      BlockAlignment.Center -> Layout.Alignment.ALIGN_CENTER
      BlockAlignment.End -> Layout.Alignment.ALIGN_OPPOSITE
      BlockAlignment.Start,
      null -> Layout.Alignment.ALIGN_NORMAL
    }

  private fun ruleStyle(): BlockStyle {
    val paint =
      TextPaint().apply {
        color = Color.BLACK
        strokeWidth = density.coerceAtLeast(1f)
        isAntiAlias = false
      }

    return BlockStyle(
      paint = paint,
      lineSpacingMultiplier = 1f,
      lineSpacingAdd = 0f,
      hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE,
      alignment = Layout.Alignment.ALIGN_NORMAL,
      breakStrategy = Layout.BREAK_STRATEGY_SIMPLE,
      indentPx = 0f,
      prefixWidthPx = 0f,
      marginTopPx = preferences.paragraphSpacing * fontSizePx,
      marginBottomPx = preferences.paragraphSpacing * fontSizePx,
    )
  }

  private fun quoteStyle(): BlockStyle {
    val leadingRuleStrokeWidthPx = density.coerceAtLeast(1f)
    val leadingRuleGapPx = fontSizePx * 0.6f

    return BlockStyle(
      paint = textPaint(),
      lineSpacingMultiplier = preferences.lineSpacing,
      lineSpacingAdd = 0f,
      hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL,
      alignment = Layout.Alignment.ALIGN_NORMAL,
      breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY,
      indentPx = 0f,
      prefixWidthPx = leadingRuleGapPx,
      marginTopPx = preferences.paragraphSpacing * fontSizePx,
      marginBottomPx = preferences.paragraphSpacing * fontSizePx,
      leadingRuleOffsetXPx = 0f,
      leadingRuleStrokeWidthPx = leadingRuleStrokeWidthPx,
    )
  }

  private fun listStyle(): BlockStyle {
    return BlockStyle(
      paint = textPaint(),
      lineSpacingMultiplier = preferences.lineSpacing,
      lineSpacingAdd = 0f,
      hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL,
      alignment = Layout.Alignment.ALIGN_NORMAL,
      breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY,
      indentPx = 0f,
      prefixWidthPx = fontSizePx * 1.4f,
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
