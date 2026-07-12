package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.StylingCensus
import org.jsoup.nodes.Document

/**
 * Collects the styling census from a spine item's DOM: how the publisher expresses styling
 * (classes, inline styles, stylesheets), regardless of whether Kanshu honors any of it yet.
 */
internal object StylingCensusCollector {
  fun collect(document: Document, baseHref: String?): StylingCensus {
    var classAttributeCount = 0
    var styleAttributeCount = 0
    val classNameCounts = linkedMapOf<String, Int>()
    val inlinePropertyCounts = linkedMapOf<String, Int>()

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
        parseDeclarationProperties(styleAttr).forEach { property ->
          inlinePropertyCounts.merge(property, 1, Int::plus)
        }
      }
    }

    val stylesheetHrefs =
      document.select("link[rel=stylesheet]").mapNotNull { link ->
        link.attr("href").trim().takeIf { it.isNotEmpty() }?.let { resolveHref(it, baseHref) }
      }

    return StylingCensus(
      classAttributeCount = classAttributeCount,
      styleAttributeCount = styleAttributeCount,
      classNameCounts = classNameCounts,
      inlinePropertyCounts = inlinePropertyCounts,
      stylesheetHrefs = stylesheetHrefs,
      styleTagCount = document.select("style").size,
    )
  }

  /** Property names from a `style=""` declaration list; tolerant of malformed segments. */
  private fun parseDeclarationProperties(styleAttr: String): List<String> =
    styleAttr.split(';').mapNotNull { declaration ->
      val property = declaration.substringBefore(':', "").trim().lowercase()
      property.takeIf { it.isNotEmpty() && declaration.contains(':') }
    }

  private val WHITESPACE = Regex("\\s+")
}
