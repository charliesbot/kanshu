package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.model.HorizontalRule
import com.charliesbot.kanshu.navigator.model.ListBlock
import com.charliesbot.kanshu.navigator.model.ListItem
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.QuoteBlock
import com.charliesbot.kanshu.navigator.model.ReaderBlock
import com.charliesbot.kanshu.navigator.model.TextLeaf
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Block parser for native reader structure.
 *
 * Unsupported structures still unwrap to text with diagnostics. Lists, quotes, and images are kept
 * lossy until their renderer slices land.
 */
internal class BlockLevelParser(private val diagnostics: ParseDiagnosticsCollector) {
  private val inlineSpanExtractor = InlineSpanExtractor(diagnostics)

  fun parse(nodes: List<Node>): List<ReaderBlock> {
    val blocks = mutableListOf<ReaderBlock>()
    for (node in nodes) {
      when (node) {
        is TextNode -> {
          if (node.text().isBlank()) continue
          paragraphFromInline(listOf(node))?.let(blocks::add)
        }

        is Element -> parseElement(node, blocks)
      }
    }
    return blocks
  }

  private fun parseElement(element: Element, blocks: MutableList<ReaderBlock>) {
    val tag = element.tagName().lowercase()
    when (tag) {
      "p" -> paragraphFromInline(element.childNodes())?.let(blocks::add)

      in HtmlTagSets.TEXT_INLINE_TAGS -> paragraphFromInline(listOf(element))?.let(blocks::add)

      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6" -> headingFromInline(tag, element.childNodes())?.let(blocks::add)

      "div" -> parseInlineOrChildren(element, blocks)

      "nav" -> appendParsed(element.childNodes(), blocks)

      "blockquote" -> quoteFromChildren(element.childNodes())?.let(blocks::add)

      "section",
      "article" -> appendParsed(element.childNodes(), blocks)

      "li" -> parseInlineOrChildren(element, blocks)

      "ul",
      "ol" -> listFromChildren(ordered = tag == "ol", element = element)?.let(blocks::add)

      "img" -> {
        diagnostics.recordUnsupportedBlock("img")
        altParagraph(element)?.let(blocks::add)
      }

      "hr" -> blocks.add(HorizontalRule)

      "table" -> {
        diagnostics.recordUnsupportedBlock("table")
        textParagraph(element.text())?.let(blocks::add)
      }

      else -> {
        diagnostics.recordUnsupportedBlock(element.tagName())
        appendParsed(element.childNodes(), blocks)
      }
    }
  }

  private fun parseInlineOrChildren(element: Element, blocks: MutableList<ReaderBlock>) {
    if (element.hasBlockChild()) {
      appendParsed(element.childNodes(), blocks)
    } else {
      paragraphFromInline(element.childNodes())?.let(blocks::add)
    }
  }

  private fun appendParsed(nodes: List<Node>, blocks: MutableList<ReaderBlock>) {
    blocks.addAll(parse(nodes))
  }

  private fun paragraphFromInline(nodes: List<Node>): ParagraphBlock? {
    val spans = inlineSpanExtractor.extract(nodes)
    return if (spans.isEmpty()) null else ParagraphBlock(spans)
  }

  private fun headingFromInline(tag: String, nodes: List<Node>): HeadingBlock? {
    val spans = inlineSpanExtractor.extract(nodes)
    if (spans.isEmpty()) return null
    val level = tag.removePrefix("h").toIntOrNull()?.coerceIn(1, 6) ?: 1
    return HeadingBlock(level = level, spans = spans)
  }

  private fun quoteFromChildren(nodes: List<Node>): QuoteBlock? {
    val children = parse(nodes)
    return if (children.isEmpty()) null else QuoteBlock(children)
  }

  private fun listFromChildren(ordered: Boolean, element: Element): ListBlock? {
    val items =
      element.select("> li").mapNotNull { listItem ->
        val blocks =
          if (listItem.hasBlockChild()) {
            parse(listItem.childNodes())
          } else {
            paragraphFromInline(listItem.childNodes())?.let(::listOf).orEmpty()
          }
        if (blocks.isEmpty()) null else ListItem(blocks)
      }
    return if (items.isEmpty()) null else ListBlock(ordered = ordered, items = items)
  }

  private fun altParagraph(element: Element): ParagraphBlock? =
    altTextLeaf(element)?.let { ParagraphBlock(listOf(it)) }

  private fun textParagraph(text: String): ParagraphBlock? {
    val trimmed = text.trim()
    return if (trimmed.isEmpty()) null else ParagraphBlock(listOf(TextLeaf(trimmed)))
  }

  private fun Element.hasBlockChild(): Boolean =
    children().any { child -> child.tagName().lowercase() !in HtmlTagSets.LAYOUT_INLINE_TAGS }
}
