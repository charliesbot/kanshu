package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.TextLeaf
import org.jsoup.nodes.Element

/**
 * Shared XHTML tag sets for the Phase 0 parser.
 *
 * [LAYOUT_INLINE_TAGS] controls `<div>` block-child detection. It includes deferred tags ([sub],
 * [sup], [ruby]) that still unwrap as inline layout but record unsupported-inline diagnostics
 * during span extraction. Keep this set in sync with [InlineSpanExtractor] when adding inline tag
 * support.
 */
internal object HtmlTagSets {
  val LAYOUT_INLINE_TAGS =
    setOf("span", "a", "em", "i", "strong", "b", "br", "sub", "sup", "ruby", "img")

  val TEXT_INLINE_TAGS = setOf("span", "a", "em", "i", "strong", "b", "br", "sub", "sup", "ruby")
}

internal fun altTextLeaf(element: Element, style: InlineStyle = InlineStyle.Plain): TextLeaf? {
  val alt = element.attr("alt").trim()
  return if (alt.isEmpty()) null else TextLeaf(text = alt, style = style)
}
