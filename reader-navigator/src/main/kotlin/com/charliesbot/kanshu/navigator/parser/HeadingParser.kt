package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.BlockSpacing
import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.parser.css.CssDisplay
import com.charliesbot.kanshu.navigator.parser.css.InheritedStyleResolver
import org.jsoup.nodes.Element

/** Parses heading elements, including the admitted CSS block-promotion subset. */
internal class HeadingParser(
  private val spans: InlineSpanExtractor,
  private val styles: InheritedStyleResolver?,
) {
  fun parse(element: Element): List<HeadingBlock> {
    val level = element.tagName().removePrefix("h").toIntOrNull()?.coerceIn(1, 6) ?: 1
    val promoted = promotedDescendants(element)
    if (promoted.isNotEmpty()) {
      val outerSpacing = styles?.resolve(element)?.blockSpacing()
      val promotedContent = promoted.mapNotNull { child ->
        spans.extractPromoted(child, element).takeIf { it.isNotEmpty() }?.let { child to it }
      }
      return promotedContent.mapIndexed { index, (child, content) ->
        val childStyle = styles?.resolve(child)
        HeadingBlock(
          level = level,
          spans = content,
          alignment = childStyle?.blockAlignment(),
          spacing =
            promotedSpacing(
              outer = outerSpacing,
              child = childStyle?.blockSpacing(),
              index = index,
              lastIndex = promotedContent.lastIndex,
            ),
        )
      }
    }

    val content = spans.extract(element.childNodes(), spans.effectiveCssEmphasis(element))
    return if (content.isEmpty()) emptyList()
    else
      listOf(
        HeadingBlock(
          level = level,
          spans = content,
          alignment = styles?.resolve(element)?.blockAlignment(),
          spacing = styles?.resolve(element)?.blockSpacing(),
        )
      )
  }

  /**
   * Returns maximal `display:block` descendants only when they cover every meaningful heading leaf.
   * Mixed inline/block headings keep the existing single-block fallback rather than risk dropping
   * or reordering text while Kanshu deliberately lacks general CSS anonymous boxes.
   */
  private fun promotedDescendants(heading: Element): List<Element> {
    val resolver = styles ?: return emptyList()
    val blockDescendants =
      heading.allElements.drop(1).filter { resolver.resolve(it).display == CssDisplay.Block }
    val candidates = blockDescendants.filter { candidate ->
      candidate
        .parents()
        .takeWhile { it !== heading }
        .none { ancestor -> resolver.resolve(ancestor).display == CssDisplay.Block }
    }
    if (candidates.isEmpty()) return emptyList()
    val hasNestedBlock = blockDescendants.any { descendant ->
      candidates.any { candidate ->
        candidate !== descendant && candidate.isAncestorOf(descendant)
      }
    }
    if (hasNestedBlock) return emptyList()

    val uncoveredText =
      heading.allElements
        .flatMap(Element::textNodes)
        .filterNot { it.text().isBlank() }
        .any { text ->
          candidates.none { candidate ->
            candidate === text.parent() || candidate.isAncestorOf(text.parent())
          }
        }
    val uncoveredImages =
      heading.select("img").any { image ->
        candidates.none { candidate -> candidate === image || candidate.isAncestorOf(image) }
      }
    val uncoveredBreaks =
      heading.select("br").any { lineBreak ->
        candidates.none { candidate ->
          candidate === lineBreak || candidate.isAncestorOf(lineBreak)
        }
      }
    return if (uncoveredText || uncoveredImages || uncoveredBreaks) emptyList() else candidates
  }

  private fun Element.isAncestorOf(descendant: Element?): Boolean =
    generateSequence(descendant?.parent()) { it.parent() }.any { it === this }

  /**
   * Projects nested heading spacing onto Kanshu's flat block list. Outer vertical margins wrap the
   * group once; missing internal margins become zero so renderer defaults are not repeated between
   * title components. Nested horizontal margins are cumulative and retain the PRD's 6em clamp.
   */
  private fun promotedSpacing(
    outer: BlockSpacing?,
    child: BlockSpacing?,
    index: Int,
    lastIndex: Int,
  ): BlockSpacing {
    val first = index == 0
    val last = index == lastIndex
    return BlockSpacing(
      marginTopEm =
        if (first) collapsedMargin(outer?.marginTopEm, child?.marginTopEm)
        else child?.marginTopEm ?: 0f,
      marginBottomEm =
        if (last) collapsedMargin(outer?.marginBottomEm, child?.marginBottomEm)
        else child?.marginBottomEm ?: 0f,
      marginStartEm = cumulativeInset(outer?.marginStartEm, child?.marginStartEm),
      marginEndEm = cumulativeInset(outer?.marginEndEm, child?.marginEndEm),
      textIndentEm = child?.textIndentEm,
    )
  }

  private fun collapsedMargin(outer: Float?, child: Float?): Float? =
    when {
      outer == null -> child
      child == null -> outer
      else -> maxOf(outer, child)
    }

  private fun cumulativeInset(outer: Float?, child: Float?): Float? =
    if (outer == null && child == null) null
    else ((outer ?: 0f) + (child ?: 0f)).coerceAtMost(MAX_HORIZONTAL_INSET_EM)

  private companion object {
    const val MAX_HORIZONTAL_INSET_EM = 6f
  }
}
