package com.charliesbot.kanshu.navigator.engine

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.charliesbot.kanshu.core.reader.SourceElementPath
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

internal class FlattenedText(
  val text: String,
  val sourcePaths: List<SourceElementPath?>,
  internal val styleRanges: List<TextStyleRange>,
  internal val linkRanges: List<LinkRange>,
) {
  fun styledText(): CharSequence {
    val builder = SpannableStringBuilder(text)
    styleRanges.forEach { range -> applyStyle(builder, range.start, range.end, range.style) }
    linkRanges.forEach { range ->
      builder.setSpan(
        EpubLinkSpan(range.href),
        range.start,
        range.end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
      builder.setSpan(
        UnderlineSpan(),
        range.start,
        range.end,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
    }
    return builder
  }
}

internal data class TextStyleRange(
  val start: Int,
  val end: Int,
  val style: InlineStyle,
)

internal data class LinkRange(val start: Int, val end: Int, val href: String)

internal data class FlattenedListRun(
  val content: FlattenedText,
  val ordered: Boolean,
  val itemIndex: Int,
  val depth: Int,
  val startsItem: Boolean,
)

/** Owns the reader's single text-flattening implementation, including source ownership. */
internal object SpanFlattener {
  fun flatten(block: ReaderBlock): CharSequence? = flattenWithSources(block)?.styledText()

  fun flatten(item: ListItem): CharSequence? = flattenWithSources(item)?.styledText()

  /** The exact stream consumed by layout, paired with the source element for every character. */
  fun sourceStream(blocks: List<ReaderBlock>): FlattenedText {
    val builder = FlattenedTextBuilder()
    blocks.forEach { block ->
      if (block is ListBlock) {
        flattenListRuns(block).forEach { builder.append(it.content) }
      } else {
        flattenWithSources(block)?.let(builder::append)
      }
    }
    return builder.build()
  }

  /** Splits lists into the same independently laid-out text runs consumed by the layout engine. */
  fun flattenListRuns(block: ListBlock): List<FlattenedListRun> = buildList {
    fun appendList(list: ListBlock, depth: Int) {
      list.items.forEachIndexed { itemIndex, item ->
        val buffered = mutableListOf<ReaderBlock>()
        var emittedItemText = false

        fun flush() {
          val content = flattenWithSources(ListItem(buffered))
          if (content != null) {
            add(
              FlattenedListRun(
                content = content,
                ordered = list.ordered,
                itemIndex = itemIndex,
                depth = depth,
                startsItem = !emittedItemText,
              )
            )
            if (content.text.isNotBlank()) emittedItemText = true
          }
          buffered.clear()
        }

        item.blocks.forEach { child ->
          if (child is ListBlock) {
            flush()
            appendList(child, depth + 1)
          } else {
            buffered += child
          }
        }
        flush()
      }
    }

    appendList(block, depth = 0)
  }

  private fun flattenWithSources(block: ReaderBlock): FlattenedText? =
    when (block) {
      is HeadingBlock -> flattenSpans(block.spans)
      is HorizontalRule -> null
      is ImageBlock -> null
      is ListBlock -> flattenList(block)
      is ParagraphBlock -> flattenSpans(block.spans)
      is QuoteBlock -> flattenBlocks(block.children)
    }

  private fun flattenWithSources(item: ListItem): FlattenedText? = flattenBlocks(item.blocks)

  private fun flattenList(block: ListBlock): FlattenedText? {
    val builder = FlattenedTextBuilder()
    block.items.forEach { item ->
      val itemText = flattenWithSources(item)
      if (itemText == null || itemText.text.isBlank()) return@forEach
      if (builder.isNotEmpty()) builder.append("\n")
      builder.append(itemText)
    }
    return builder.buildOrNull()
  }

  private fun flattenBlocks(blocks: List<ReaderBlock>): FlattenedText? {
    val builder = FlattenedTextBuilder()
    blocks.forEach { child ->
      val childText = flattenWithSources(child)
      if (childText == null || childText.text.isBlank()) return@forEach
      if (builder.isNotEmpty()) builder.append("\n\n")
      builder.append(childText)
    }
    return builder.buildOrNull()
  }

  private fun flattenSpans(spans: List<TextSpan>): FlattenedText {
    val builder = FlattenedTextBuilder()
    spans.forEach { appendSpan(builder, it) }
    return builder.build()
  }

  private fun appendSpan(builder: FlattenedTextBuilder, span: TextSpan) {
    when (span) {
      is TextLeaf -> {
        val start = builder.length
        builder.append(span.text, span.sourceElementPath)
        builder.addStyle(start, builder.length, span.style)
      }

      is StyledGroup -> {
        val start = builder.length
        span.children.forEach { appendSpan(builder, it) }
        builder.addStyle(start, builder.length, span.style)
      }

      is LinkSpan -> {
        val start = builder.length
        span.children.forEach { appendSpan(builder, it) }
        builder.addLink(start, builder.length, span.href)
      }
    }
  }
}

private class FlattenedTextBuilder {
  private val text = StringBuilder()
  private val sourcePaths = mutableListOf<SourceElementPath?>()
  private val styleRanges = mutableListOf<TextStyleRange>()
  private val linkRanges = mutableListOf<LinkRange>()
  val length: Int
    get() = text.length

  fun isNotEmpty(): Boolean = text.isNotEmpty()

  fun append(value: String, sourcePath: SourceElementPath? = null) {
    text.append(value)
    repeat(value.length) { sourcePaths += sourcePath }
  }

  fun append(value: FlattenedText) {
    val offset = length
    text.append(value.text)
    sourcePaths += value.sourcePaths
    styleRanges +=
      value.styleRanges.map { it.copy(start = it.start + offset, end = it.end + offset) }
    linkRanges += value.linkRanges.map { it.copy(start = it.start + offset, end = it.end + offset) }
  }

  fun addStyle(start: Int, end: Int, style: InlineStyle) {
    if (start < end && style != InlineStyle.Plain) {
      styleRanges += TextStyleRange(start, end, style)
    }
  }

  fun addLink(start: Int, end: Int, href: String) {
    if (start < end) linkRanges += LinkRange(start, end, href)
  }

  fun build(): FlattenedText =
    FlattenedText(
      text = text.toString(),
      sourcePaths = sourcePaths.toList(),
      styleRanges = styleRanges.toList(),
      linkRanges = linkRanges.toList(),
    )

  fun buildOrNull(): FlattenedText? = if (text.isEmpty()) null else build()
}

private fun applyStyle(
  builder: SpannableStringBuilder,
  start: Int,
  end: Int,
  style: InlineStyle,
) {
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

internal data class EpubLinkSpan(val href: String)
