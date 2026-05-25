package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.TextLeaf
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Phase 0 block parser: always emits [ParagraphBlock] only.
 *
 * Headings, quotes, lists, images, and rules unwrap to flat paragraphs with preserved text. Rich
 * [ReaderBlock] variants from the scaffold exist for Phase 1 layout and rendering, not this parser
 * slice. See `docs/PRD_NATIVE_READER.md` Phase 0 parser output.
 */
internal class BlockLevelParser(private val diagnostics: ParseDiagnosticsCollector) {
  private val inlineSpanExtractor = InlineSpanExtractor(diagnostics)

  fun parse(nodes: List<Node>): List<ParagraphBlock> {
    val paragraphs = mutableListOf<ParagraphBlock>()
    for (node in nodes) {
      when (node) {
        is TextNode -> {
          if (node.text().isBlank()) continue
          paragraphFromInline(listOf(node))?.let(paragraphs::add)
        }

        is Element -> parseElement(node, paragraphs)
      }
    }
    return paragraphs
  }

  private fun parseElement(element: Element, paragraphs: MutableList<ParagraphBlock>) {
    when (element.tagName().lowercase()) {
      "p" -> paragraphFromInline(element.childNodes())?.let(paragraphs::add)

      "div",
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6" -> parseInlineOrChildren(element, paragraphs)

      "section",
      "article",
      "blockquote",
      "li" -> appendParsed(element.childNodes(), paragraphs)

      "ul",
      "ol" -> {
        val listItems = element.select("> li")
        if (listItems.isEmpty()) {
          appendParsed(element.childNodes(), paragraphs)
        } else {
          listItems.forEach { listItem -> appendParsed(listItem.childNodes(), paragraphs) }
        }
      }

      "img" -> {
        diagnostics.recordUnsupportedBlock("img")
        altParagraph(element)?.let(paragraphs::add)
      }

      "hr" -> diagnostics.recordUnsupportedBlock("hr")

      "table" -> {
        diagnostics.recordUnsupportedBlock("table")
        textParagraph(element.text())?.let(paragraphs::add)
      }

      else -> {
        diagnostics.recordUnsupportedBlock(element.tagName())
        appendParsed(element.childNodes(), paragraphs)
      }
    }
  }

  private fun parseInlineOrChildren(element: Element, paragraphs: MutableList<ParagraphBlock>) {
    if (element.hasBlockChild()) {
      appendParsed(element.childNodes(), paragraphs)
    } else {
      paragraphFromInline(element.childNodes())?.let(paragraphs::add)
    }
  }

  private fun appendParsed(nodes: List<Node>, paragraphs: MutableList<ParagraphBlock>) {
    paragraphs.addAll(parse(nodes))
  }

  private fun paragraphFromInline(nodes: List<Node>): ParagraphBlock? {
    val spans = inlineSpanExtractor.extract(nodes)
    return if (spans.isEmpty()) null else ParagraphBlock(spans)
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
