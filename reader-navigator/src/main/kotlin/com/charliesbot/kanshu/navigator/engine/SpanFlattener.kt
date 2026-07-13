package com.charliesbot.kanshu.navigator.engine

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.model.HorizontalRule
import com.charliesbot.kanshu.navigator.model.ImageBlock
import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.LinkSpan
import com.charliesbot.kanshu.navigator.model.ListBlock
import com.charliesbot.kanshu.navigator.model.ListItem
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.QuoteBlock
import com.charliesbot.kanshu.navigator.model.ReaderBlock
import com.charliesbot.kanshu.navigator.model.StyledGroup
import com.charliesbot.kanshu.navigator.model.TextLeaf
import com.charliesbot.kanshu.navigator.model.TextSpan

internal object SpanFlattener {
  fun flatten(block: ReaderBlock): CharSequence? =
    when (block) {
      is HeadingBlock -> flattenSpans(block.spans)
      is HorizontalRule -> null
      is ImageBlock -> null
      is ListBlock -> flattenList(block)
      is ParagraphBlock -> flattenSpans(block.spans)
      is QuoteBlock -> flattenQuote(block)
    }

  fun flatten(item: ListItem): CharSequence? = flattenBlocks(item.blocks)

  private fun flattenQuote(block: QuoteBlock): CharSequence? {
    return flattenBlocks(block.children)
  }

  private fun flattenList(block: ListBlock): CharSequence? {
    val builder = SpannableStringBuilder()
    block.items.forEach { item ->
      val itemText = flatten(item)
      if (itemText.isNullOrBlank()) return@forEach
      if (builder.isNotEmpty()) builder.append("\n")
      builder.append(itemText)
    }
    return if (builder.isEmpty()) null else builder
  }

  private fun flattenBlocks(blocks: List<ReaderBlock>): CharSequence? {
    val builder = SpannableStringBuilder()
    blocks.forEach { child ->
      val childText =
        when (child) {
          is HeadingBlock -> flattenSpans(child.spans)
          is ImageBlock -> null
          is ListBlock -> flattenList(child)
          is ParagraphBlock -> flattenSpans(child.spans)
          is QuoteBlock -> flattenQuote(child)
          is HorizontalRule -> null
        }
      if (childText.isNullOrBlank()) return@forEach
      if (builder.isNotEmpty()) builder.append("\n\n")
      builder.append(childText)
    }
    return if (builder.isEmpty()) null else builder
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

      is StyledGroup -> {
        val start = builder.length
        span.children.forEach { appendSpan(builder, it) }
        applyStyle(builder, start, builder.length, span.style)
      }

      is LinkSpan -> {
        val start = builder.length
        span.children.forEach { appendSpan(builder, it) }
        if (start < builder.length) {
          builder.setSpan(
            EpubLinkSpan(span.href),
            start,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
          )
          // UA convention: links read as links. Underline is the whole affordance on a B&W panel.
          builder.setSpan(UnderlineSpan(), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
      }
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

internal data class EpubLinkSpan(val href: String)
