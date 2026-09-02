package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.BlockAlignment
import com.charliesbot.kanshu.navigator.model.BlockSpacing
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
  private val styles: InheritedStyleResolver,
) {
  private val inlineSpanExtractor = InlineSpanExtractor(diagnostics, styles, baseHref)
  private val headingParser = HeadingParser(inlineSpanExtractor, styles)

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
        // Anchors (TOC/nav pages) and other inline tags sometimes wrap whole paragraphs;
        // promote the blocks instead of flattening them into one line.
        blocks.addAll(
          blocksFromContainer(
            element = element,
            inlineNodes = listOf(element),
            recognizeImageOnly = false,
          )
        )

      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6" -> blocks.addAll(headingParser.parse(element))

      "div" -> blocks.addAll(blocksFromContainer(element))

      "nav" -> appendParsed(element.childNodes(), blocks)

      "blockquote" -> quoteFromChildren(element.childNodes())?.let(blocks::add)

      "section",
      "article" -> appendParsed(element.childNodes(), blocks)

      "li" -> blocks.addAll(blocksFromContainer(element))

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

  private fun appendParsed(nodes: List<Node>, blocks: MutableList<ReaderBlock>) {
    blocks.addAll(parse(nodes))
  }

  private fun blocksFromContainer(
    element: Element,
    inlineNodes: List<Node> = element.childNodes(),
    recognizeImageOnly: Boolean = true,
  ): List<ReaderBlock> {
    if (recognizeImageOnly)
      imageOnlyBlock(element)?.let {
        return listOf(it)
      }
    return if (element.hasBlockDescendant()) {
      parse(element.childNodes())
    } else {
      paragraphFromInline(inlineNodes, element)?.let(::listOf).orEmpty()
    }
  }

  private fun paragraphFromInline(nodes: List<Node>, owner: Element? = null): ParagraphBlock? {
    val spans = inlineSpanExtractor.extract(nodes, baseStyleFor(owner))
    return if (spans.isEmpty()) null
    else
      ParagraphBlock(
        spans,
        alignment = publisherAlignment(owner),
        spacing = publisherSpacing(owner),
      )
  }

  private fun baseStyleFor(owner: Element?): InlineStyle =
    if (owner == null) InlineStyle.Plain else inlineSpanExtractor.effectiveCssEmphasis(owner)

  private fun publisherAlignment(owner: Element?): BlockAlignment? = owner?.let {
    styles.resolve(it).blockAlignment()
  }

  private fun publisherSpacing(owner: Element?): BlockSpacing? = owner?.let {
    styles.resolve(it).blockSpacing()
  }

  private fun quoteFromChildren(nodes: List<Node>): QuoteBlock? {
    val children = parse(nodes)
    return if (children.isEmpty()) null else QuoteBlock(children)
  }

  private fun listFromChildren(ordered: Boolean, element: Element): ListBlock? {
    val items =
      element.select("> li").mapNotNull { listItem ->
        val blocks = blocksFromContainer(listItem, recognizeImageOnly = false)
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
    else
      ParagraphBlock(
        listOf(TextLeaf(trimmed, sourceElementPath = owner?.sourceElementPath())),
        alignment = publisherAlignment(owner),
        spacing = publisherSpacing(owner),
      )
  }

  private fun Element.hasBlockDescendant(): Boolean = allElements.any { descendant ->
    descendant !== this && descendant.tagName().lowercase() in HtmlTagSets.BLOCK_TAGS
  }
}
