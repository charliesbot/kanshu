package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.ParseResult
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Parses EPUB spine XHTML into a [ParseResult].
 *
 * Parses semantic block structure with bold/italic inline spans. Unsupported structure unwraps to
 * text; tag counts live in [ParseResult.diagnostics], not on the reading surface.
 */
object EpubParser {
  fun parse(xhtml: String): ParseResult {
    val diagnostics = ParseDiagnosticsCollector()
    if (xhtml.isBlank()) {
      return ParseResult(ReaderDocument(blocks = emptyList()), diagnostics.build())
    }

    val document = Jsoup.parse(xhtml)
    val blocks = BlockLevelParser(diagnostics).parse(document.body().childNodes())

    return ParseResult(
      document = ReaderDocument(blocks = blocks, language = extractLanguage(document)),
      diagnostics = diagnostics.build(),
    )
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
