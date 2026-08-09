package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.StylingCensus
import com.charliesbot.kanshu.navigator.parser.css.CssDisplay
import com.charliesbot.kanshu.navigator.parser.css.CssStyleResolver
import com.charliesbot.kanshu.navigator.parser.css.CssStylesheet
import com.charliesbot.kanshu.navigator.parser.css.InheritedStyleResolver
import com.charliesbot.kanshu.navigator.parser.css.parseInlineDeclarations
import org.jsoup.nodes.Document

/**
 * Collects the styling census from a spine item's DOM: how the publisher expresses styling
 * (classes, inline styles, stylesheets), regardless of whether Kanshu honors any of it yet.
 */
internal object StylingCensusCollector {
  fun collect(
    document: Document,
    baseHref: String?,
    stylesheets: List<CssStylesheet> = emptyList(),
  ): StylingCensus {
    var classAttributeCount = 0
    var styleAttributeCount = 0
    val classNameCounts = linkedMapOf<String, Int>()
    val inlinePropertyCounts = linkedMapOf<String, Int>()
    val blockDisplayContextCounts = linkedMapOf<String, Int>()
    val styleResolver = InheritedStyleResolver(CssStyleResolver(stylesheets))

    document.body().select("*").forEach { element ->
      val classAttr = element.attr("class").trim()
      if (classAttr.isNotEmpty()) {
        classAttributeCount++
        classAttr.split(WHITESPACE).forEach { name ->
          classNameCounts.merge(name, 1, Int::plus)
        }
      }
      val styleAttr = element.attr("style").trim()
      if (styleAttr.isNotEmpty()) {
        styleAttributeCount++
        parseInlineDeclarations(styleAttr).forEach { declaration ->
          inlinePropertyCounts.merge(declaration.property, 1, Int::plus)
        }
      }
      if (
        element.tagName().lowercase() in HtmlTagSets.TEXT_INLINE_TAGS &&
          styleResolver.resolve(element).display == CssDisplay.Block
      ) {
        val owner =
          element.parents().firstOrNull {
            it.tagName().lowercase() in HtmlTagSets.BLOCK_TAGS
          }
        val classes =
          element
            .classNames()
            .takeIf { it.isNotEmpty() }
            ?.sorted()
            ?.joinToString(".", prefix = ".")
            .orEmpty()
        val context =
          "${element.tagName().lowercase()}$classes inside ${owner?.tagName() ?: "root"}"
        blockDisplayContextCounts.merge(context, 1, Int::plus)
      }
    }

    val stylesheetHrefs = document.stylesheetLinkHrefs(baseHref)

    val stylesheetPropertyCounts = linkedMapOf<String, Int>()
    val atRuleCounts = linkedMapOf<String, Int>()
    stylesheets.forEach { sheet ->
      sheet.stats.declarationCounts.forEach { (property, count) ->
        stylesheetPropertyCounts.merge(property, count, Int::plus)
      }
      sheet.stats.atRuleCounts.forEach { (atRule, count) ->
        atRuleCounts.merge(atRule, count, Int::plus)
      }
    }

    return StylingCensus(
      classAttributeCount = classAttributeCount,
      styleAttributeCount = styleAttributeCount,
      classNameCounts = classNameCounts,
      inlinePropertyCounts = inlinePropertyCounts,
      stylesheetHrefs = stylesheetHrefs,
      styleTagCount = document.select("style").size,
      stylesheetPropertyCounts = stylesheetPropertyCounts,
      unsupportedSelectorCount = stylesheets.sumOf { it.stats.unsupportedSelectorCount },
      atRuleCounts = atRuleCounts,
      importantCount = stylesheets.sumOf { it.stats.importantCount },
      blockDisplayContextCounts = blockDisplayContextCounts,
    )
  }

  private val WHITESPACE = Regex("\\s+")
}
