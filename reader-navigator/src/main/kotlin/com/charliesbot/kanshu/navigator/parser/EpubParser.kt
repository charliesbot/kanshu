package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.ParseResult
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.parser.css.CssStyleResolver
import com.charliesbot.kanshu.navigator.parser.css.CssStylesheet
import com.charliesbot.kanshu.navigator.parser.css.InheritedStyleResolver
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Parses EPUB spine XHTML into a [ParseResult].
 *
 * Parses semantic block structure with bold/italic inline spans. Unsupported structure unwraps to
 * text; tag counts live in [ParseResult.diagnostics], not on the reading surface.
 */
object EpubParser {
  /**
   * @param baseHref publication-root-relative path of the spine item the XHTML came from; used to
   *   resolve relative resource hrefs (images) to publication-root-relative hrefs.
   * @param stylesheets parsed publisher stylesheets in document link order; emphasis and block
   *   alignment resolve through the micro-cascade (docs/PRD_PUBLISHER_STYLES.md). Inline `style`
   *   attributes are honored even when this is empty.
   */
  fun parse(
    xhtml: String,
    baseHref: String? = null,
    stylesheets: List<CssStylesheet> = emptyList(),
  ): ParseResult {
    val diagnostics = ParseDiagnosticsCollector()
    if (xhtml.isBlank()) {
      return ParseResult(ReaderDocument(blocks = emptyList()), diagnostics.build())
    }

    val document = Jsoup.parse(xhtml)
    val styles = InheritedStyleResolver(CssStyleResolver(stylesheets))
    val blocks = BlockLevelParser(diagnostics, baseHref, styles).parse(document.body().childNodes())

    return ParseResult(
      document = ReaderDocument(blocks = blocks, language = extractLanguage(document)),
      diagnostics =
        diagnostics
          .build()
          .copy(stylingCensus = StylingCensusCollector.collect(document, baseHref, stylesheets)),
    )
  }

  /**
   * Stylesheet hrefs declared by a spine item, resolved to publication-root-relative paths in
   * document order. The feature layer fetches and parses these (cached per publication) before
   * calling [parse].
   */
  fun stylesheetHrefs(xhtml: String, baseHref: String? = null): List<String> {
    if (xhtml.isBlank()) return emptyList()
    return Jsoup.parse(xhtml).select("link[rel=stylesheet]").mapNotNull { link ->
      link.attr("href").trim().takeIf { it.isNotEmpty() }?.let { resolveHref(it, baseHref) }
    }
  }

  private fun extractLanguage(document: Document): String? =
    sequenceOf(
        document.selectFirst("html[lang]")?.attr("lang"),
        document.selectFirst("html[xml:lang]")?.attr("xml:lang"),
        document.selectFirst("body[xml:lang]")?.attr("xml:lang"),
        document.selectFirst("body[lang]")?.attr("lang"),
      )
      .firstOrNull { !it.isNullOrBlank() }
}
