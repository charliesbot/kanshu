package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.BlockAlignment
import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.model.HorizontalRule
import com.charliesbot.kanshu.navigator.model.ImageBlock
import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.ListBlock
import com.charliesbot.kanshu.navigator.model.ListItem
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.QuoteBlock
import com.charliesbot.kanshu.navigator.model.ReaderBlock
import com.charliesbot.kanshu.navigator.model.TextLeaf
import com.charliesbot.kanshu.navigator.parser.css.InheritedStyleResolver
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Block parser for native reader structure.
 *
 * Unsupported structures still unwrap to text with diagnostics. Lists, quotes, and images are kept
 * lossy until their renderer slices land.
 */
internal class BlockLevelParser(
  private val diagnostics: ParseDiagnosticsCollector,
  private val baseHref: String? = null,
  private val styles: InheritedStyleResolver? = null,
) {
  private val inlineSpanExtractor = InlineSpanExtractor(diagnostics, styles)

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
      "p" ->
        imageOnlyBlock(element)?.let(blocks::add)
          ?: paragraphFromInline(element.childNodes(), element)?.let(blocks::add)

      in HtmlTagSets.TEXT_INLINE_TAGS ->
        paragraphFromInline(listOf(element), element)?.let(blocks::add)

      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6" -> headingFromInline(tag, element)?.let(blocks::add)

      "div" -> parseInlineOrChildren(element, blocks)

      "nav" -> appendParsed(element.childNodes(), blocks)

      "blockquote" -> quoteFromChildren(element.childNodes())?.let(blocks::add)

      "section",
      "article" -> appendParsed(element.childNodes(), blocks)

      "li" -> parseInlineOrChildren(element, blocks)

      "ul",
      "ol" -> listFromChildren(ordered = tag == "ol", element = element)?.let(blocks::add)

      "img" -> imageBlock(element)?.let(blocks::add)

      "hr" -> blocks.add(HorizontalRule)

      "table" -> {
        diagnostics.recordUnsupportedBlock("table")
        textParagraph(element.text(), element)?.let(blocks::add)
      }

      else -> {
        diagnostics.recordUnsupportedBlock(element.tagName())
        appendParsed(element.childNodes(), blocks)
      }
    }
  }

  private fun parseInlineOrChildren(element: Element, blocks: MutableList<ReaderBlock>) {
    imageOnlyBlock(element)?.let {
      blocks.add(it)
      return
    }
    if (element.hasBlockChild()) {
      appendParsed(element.childNodes(), blocks)
    } else {
      paragraphFromInline(element.childNodes(), element)?.let(blocks::add)
    }
  }

  private fun appendParsed(nodes: List<Node>, blocks: MutableList<ReaderBlock>) {
    blocks.addAll(parse(nodes))
  }

  private fun paragraphFromInline(nodes: List<Node>, owner: Element? = null): ParagraphBlock? {
    val spans = inlineSpanExtractor.extract(nodes, baseStyleFor(owner))
    return if (spans.isEmpty()) null
    else ParagraphBlock(spans, alignment = publisherAlignment(owner))
  }

  private fun headingFromInline(tag: String, element: Element): HeadingBlock? {
    val spans = inlineSpanExtractor.extract(element.childNodes(), baseStyleFor(element))
    if (spans.isEmpty()) return null
    val level = tag.removePrefix("h").toIntOrNull()?.coerceIn(1, 6) ?: 1
    return HeadingBlock(level = level, spans = spans, alignment = publisherAlignment(element))
  }

  private fun baseStyleFor(owner: Element?): InlineStyle =
    if (owner == null) InlineStyle.Plain else inlineSpanExtractor.effectiveCssEmphasis(owner)

  private fun publisherAlignment(owner: Element?): BlockAlignment? = owner?.let {
    styles?.resolve(it)?.blockAlignment()
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
            paragraphFromInline(listItem.childNodes(), listItem)?.let(::listOf).orEmpty()
          }
        if (blocks.isEmpty()) null else ListItem(blocks)
      }
    return if (items.isEmpty()) null else ListBlock(ordered = ordered, items = items)
  }

  private fun imageBlock(element: Element): ImageBlock? {
    val src = element.attr("src").trim()
    return ImageBlock(
      resourceHref = resolveHref(src, baseHref),
      alt = element.attr("alt").trim().ifEmpty { null },
    )
  }

  private fun imageOnlyBlock(element: Element): ImageBlock? {
    val image =
      element.children().singleOrNull { it.tagName().equals("img", ignoreCase = true) }
        ?: return null
    val textWithoutImage =
      element.childNodes().filterNot { it == image }.joinToString("") { it.toString() }
    if (textWithoutImage.isNotBlank()) return null
    return imageBlock(image)
  }

  private fun textParagraph(text: String, owner: Element? = null): ParagraphBlock? {
    val trimmed = text.trim()
    return if (trimmed.isEmpty()) null
    else ParagraphBlock(listOf(TextLeaf(trimmed)), alignment = publisherAlignment(owner))
  }

  private fun Element.hasBlockChild(): Boolean =
    children().any { child -> child.tagName().lowercase() !in HtmlTagSets.LAYOUT_INLINE_TAGS }
}
