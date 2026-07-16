package com.charliesbot.kanshu.navigator.engine

import android.graphics.text.LineBreaker
import android.text.StaticLayout

internal object StaticLayoutFactory {
  fun build(
    text: CharSequence,
    style: BlockStyle,
    contentWidthPx: Int,
    justify: Boolean,
  ): StaticLayout {
    val layoutWidth =
      (contentWidthPx - style.indentPx - style.prefixWidthPx - style.endInsetPx)
        .toInt()
        .coerceAtLeast(1)
    val builder =
      StaticLayout.Builder.obtain(text, 0, text.length, style.paint, layoutWidth)
        .setAlignment(style.alignment)
        .setLineSpacing(style.lineSpacingAdd, style.lineSpacingMultiplier)
        .setHyphenationFrequency(style.hyphenationFrequency)
        .setBreakStrategy(style.breakStrategy)

    if (style.firstLineIndentPx > 0f) {
      // Per-line left indents; the last array value repeats for remaining lines, so the first
      // line is indented and every following line sits at the column edge.
      builder.setIndents(intArrayOf(style.firstLineIndentPx.toInt(), 0), null)
    }

    if (justify && style.justifiable) {
      builder.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
    }

    return builder.build()
  }
}
