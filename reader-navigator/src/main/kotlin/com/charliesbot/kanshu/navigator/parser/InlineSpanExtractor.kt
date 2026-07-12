package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.LinkSpan
import com.charliesbot.kanshu.navigator.model.TextLeaf
import com.charliesbot.kanshu.navigator.model.TextSpan
import com.charliesbot.kanshu.navigator.parser.css.InheritedStyleResolver
import com.charliesbot.kanshu.navigator.parser.css.ResolvedStyle
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal class InlineSpanExtractor(
  private val diagnostics: ParseDiagnosticsCollector,
  private val styles: InheritedStyleResolver? = null,
) {
  fun extract(nodes: List<Node>, inheritedStyle: InlineStyle = InlineStyle.Plain): List<TextSpan> =
    trimEdgeBlankSpans(extractInternal(nodes, inheritedStyle))

  private fun extractInternal(nodes: List<Node>, inheritedStyle: InlineStyle): List<TextSpan> =
    nodes.flatMap { node ->
      when (node) {
        is TextNode -> {
          val text = node.text()
          if (text.isEmpty()) emptyList() else listOf(TextLeaf(text = text, style = inheritedStyle))
        }

        is Element ->
          when (node.tagName().lowercase()) {
            "br" -> listOf(TextLeaf(text = "\n", style = inheritedStyle))

            "strong",
            "b" -> extractInternal(node.childNodes(), styled(node, mergeBold(inheritedStyle)))

            "em",
            "i" -> extractInternal(node.childNodes(), styled(node, mergeItalic(inheritedStyle)))

            "span" -> extractInternal(node.childNodes(), styled(node, inheritedStyle))

            "a" -> extractLink(node, styled(node, inheritedStyle))

            "img" -> {
              diagnostics.recordUnsupportedInline("img")
              altTextLeaf(node, inheritedStyle)?.let(::listOf) ?: emptyList()
            }

            else -> {
              diagnostics.recordUnsupportedInline(node.tagName())
              extractInternal(node.childNodes(), styled(node, inheritedStyle))
            }
          }

        else -> emptyList()
      }
    }

  /**
   * Tag semantics first, publisher CSS on top — but only values *declared* on this element (matched
   * rules or inline style) may override. Inherited CSS values never reset a semantic tag: `p.body {
   * font-style: normal }` must not strip a nested `<em>`.
   */
  private fun styled(element: Element, tagStyle: InlineStyle): InlineStyle =
    merge(tagStyle, styles?.resolveDeclared(element))

  /**
   * Effective (inherited) emphasis for text directly inside a block element — the base style for a
   * paragraph's own text nodes.
   */
  fun effectiveCssEmphasis(element: Element): InlineStyle =
    merge(InlineStyle.Plain, styles?.resolve(element))

  private fun merge(style: InlineStyle, css: ResolvedStyle?): InlineStyle {
    if (css == null) return style
    var italic = style == InlineStyle.Italic || style == InlineStyle.BoldItalic
    var bold = style == InlineStyle.Bold || style == InlineStyle.BoldItalic
    css.italic?.let { italic = it }
    css.bold?.let { bold = it }
    return when {
      bold && italic -> InlineStyle.BoldItalic
      bold -> InlineStyle.Bold
      italic -> InlineStyle.Italic
      else -> InlineStyle.Plain
    }
  }

  private fun mergeBold(style: InlineStyle): InlineStyle =
    when (style) {
      InlineStyle.Plain -> InlineStyle.Bold
      InlineStyle.Italic -> InlineStyle.BoldItalic
      InlineStyle.Bold -> InlineStyle.Bold
      InlineStyle.BoldItalic -> InlineStyle.BoldItalic
      // Phase 0 does not parse small-caps markup; branch is exhaustive-only today.
      InlineStyle.SmallCaps -> InlineStyle.Bold
    }

  private fun mergeItalic(style: InlineStyle): InlineStyle =
    when (style) {
      InlineStyle.Plain -> InlineStyle.Italic
      InlineStyle.Bold -> InlineStyle.BoldItalic
      InlineStyle.Italic -> InlineStyle.Italic
      InlineStyle.BoldItalic -> InlineStyle.BoldItalic
      // Phase 0 does not parse small-caps markup; branch is exhaustive-only today.
      InlineStyle.SmallCaps -> InlineStyle.Italic
    }

  private fun trimEdgeBlankSpans(spans: List<TextSpan>): List<TextSpan> =
    spans.dropWhile { spanText(it).isBlank() }.dropLastWhile { spanText(it).isBlank() }

  private fun extractLink(node: Element, inheritedStyle: InlineStyle): List<TextSpan> {
    val children = extractInternal(node.childNodes(), inheritedStyle)
    if (children.isEmpty()) return emptyList()
    val href = node.attr("href")
    return if (href.isBlank()) children else listOf(LinkSpan(href = href, children = children))
  }

  private fun spanText(span: TextSpan): String =
    when (span) {
      is TextLeaf -> span.text
      is LinkSpan -> span.children.joinToString("") { spanText(it) }
      else -> ""
    }
}
