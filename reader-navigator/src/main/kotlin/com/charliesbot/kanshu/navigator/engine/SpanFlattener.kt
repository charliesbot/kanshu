package com.charliesbot.kanshu.navigator.engine

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.ReaderBlock
import com.charliesbot.kanshu.navigator.model.StyledGroup
import com.charliesbot.kanshu.navigator.model.TextLeaf
import com.charliesbot.kanshu.navigator.model.TextSpan

internal object SpanFlattener {
  fun flatten(block: ReaderBlock): CharSequence? =
    when (block) {
      is HeadingBlock -> flattenSpans(block.spans)
      is ParagraphBlock -> flattenSpans(block.spans)
      else -> null
    }

  private fun flattenSpans(spans: List<TextSpan>): CharSequence {
    val builder = SpannableStringBuilder()
    spans.forEach { appendSpan(builder, it) }
    return builder
  }

  private fun appendSpan(builder: SpannableStringBuilder, span: TextSpan) {
    when (span) {
      is TextLeaf -> {
        val start = builder.length
        builder.append(span.text)
        applyStyle(builder, start, builder.length, span.style)
      }

      is StyledGroup -> span.children.forEach { appendSpan(builder, it) }

      else -> Unit
    }
  }

  private fun applyStyle(
    builder: SpannableStringBuilder,
    start: Int,
    end: Int,
    style: InlineStyle,
  ) {
    if (start >= end) return
    when (style) {
      InlineStyle.Plain -> Unit
      InlineStyle.Bold ->
        builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      InlineStyle.Italic ->
        builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      InlineStyle.BoldItalic ->
        builder.setSpan(
          StyleSpan(Typeface.BOLD_ITALIC),
          start,
          end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
      InlineStyle.SmallCaps -> Unit
    }
  }
}
