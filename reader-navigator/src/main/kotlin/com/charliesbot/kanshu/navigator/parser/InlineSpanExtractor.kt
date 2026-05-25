package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.TextLeaf
import com.charliesbot.kanshu.navigator.model.TextSpan
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal class InlineSpanExtractor(private val diagnostics: ParseDiagnosticsCollector) {
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
            "b" -> extractInternal(node.childNodes(), mergeBold(inheritedStyle))

            "em",
            "i" -> extractInternal(node.childNodes(), mergeItalic(inheritedStyle))

            "span",
            "a" -> extractInternal(node.childNodes(), inheritedStyle)

            "img" -> {
              diagnostics.recordUnsupportedInline("img")
              altTextLeaf(node, inheritedStyle)?.let(::listOf) ?: emptyList()
            }

            else -> {
              diagnostics.recordUnsupportedInline(node.tagName())
              extractInternal(node.childNodes(), inheritedStyle)
            }
          }

        else -> emptyList()
      }
    }

  private fun mergeBold(style: InlineStyle): InlineStyle =
    when (style) {
      InlineStyle.Plain -> InlineStyle.Bold
      InlineStyle.Italic -> InlineStyle.BoldItalic
      InlineStyle.Bold,
      InlineStyle.BoldItalic -> InlineStyle.BoldItalic
      // Phase 0 does not parse small-caps markup; branch is exhaustive-only today.
      InlineStyle.SmallCaps -> InlineStyle.Bold
    }

  private fun mergeItalic(style: InlineStyle): InlineStyle =
    when (style) {
      InlineStyle.Plain -> InlineStyle.Italic
      InlineStyle.Bold -> InlineStyle.BoldItalic
      InlineStyle.Italic,
      InlineStyle.BoldItalic -> InlineStyle.BoldItalic
      // Phase 0 does not parse small-caps markup; branch is exhaustive-only today.
      InlineStyle.SmallCaps -> InlineStyle.Italic
    }

  private fun trimEdgeBlankSpans(spans: List<TextSpan>): List<TextSpan> =
    spans.dropWhile { spanText(it).isBlank() }.dropLastWhile { spanText(it).isBlank() }

  private fun spanText(span: TextSpan): String = (span as? TextLeaf)?.text.orEmpty()
}
