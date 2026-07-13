package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.TextLeaf
import org.jsoup.nodes.Element

/**
 * Shared XHTML tag sets for the parser.
 *
 * [TEXT_INLINE_TAGS] includes deferred tags ([sub], [sup], [ruby]) that unwrap as inline text but
 * record unsupported-inline diagnostics during span extraction. Keep in sync with
 * [InlineSpanExtractor] when adding inline tag support.
 */
internal object HtmlTagSets {
  val TEXT_INLINE_TAGS = setOf("span", "a", "em", "i", "strong", "b", "br", "sub", "sup", "ruby")

  /**
   * Tags that produce block structure when found as descendants of an inline-classified container.
   * Deliberately an allowlist: unknown tags (`<q>`, `<cite>`, `<code>`, `<font>`…) must keep
   * flattening inline — treating them as blocks would fragment sentences.
   */
  val BLOCK_TAGS =
    setOf(
      "p",
      "div",
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "blockquote",
      "ul",
      "ol",
      "li",
      "hr",
      "table",
      "section",
      "article",
      "nav",
      "aside",
      "figure",
      "figcaption",
      "pre",
      "dl",
      "dt",
      "dd",
      "header",
      "footer",
      "main",
    )
}

internal fun altTextLeaf(element: Element, style: InlineStyle = InlineStyle.Plain): TextLeaf? {
  val alt = element.attr("alt").trim()
  return if (alt.isEmpty()) null else TextLeaf(text = alt, style = style)
}
