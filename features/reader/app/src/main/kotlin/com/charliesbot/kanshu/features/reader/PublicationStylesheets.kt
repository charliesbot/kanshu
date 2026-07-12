package com.charliesbot.kanshu.features.reader

import android.util.Log
import com.charliesbot.kanshu.navigator.parser.css.CssParser
import com.charliesbot.kanshu.navigator.parser.css.CssStylesheet
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

private const val TAG = "PublicationStylesheets"

// Degradation cap from docs/PRD_PUBLISHER_STYLES.md — real book sheets are KB-scale.
private const val MAX_STYLESHEET_BYTES = 256 * 1024

/**
 * Fetches and parses a publication's stylesheets, cached by resolved href for the publication's
 * lifetime — chapters overwhelmingly share one or two sheets, so each parses once per book. A null
 * cache entry marks an unreadable or oversized sheet so it is not refetched.
 */
internal class PublicationStylesheets(private val publication: Publication) {
  private val cache = mutableMapOf<String, CssStylesheet?>()

  suspend fun forHrefs(hrefs: List<String>): List<CssStylesheet> = hrefs.mapNotNull { href ->
    if (!cache.containsKey(href)) {
      cache[href] = load(href)
    }
    cache[href]
  }

  private suspend fun load(href: String): CssStylesheet? {
    val url = Url.fromDecodedPath(href) ?: return null
    val bytes = publication.get(url)?.read()?.getOrNull()
    if (bytes == null) {
      Log.d(TAG, "stylesheet unavailable href=$href")
      return null
    }
    if (bytes.size > MAX_STYLESHEET_BYTES) {
      Log.d(TAG, "stylesheet skipped, oversized (${bytes.size} bytes) href=$href")
      return null
    }
    // The parser is exception-free by contract, but a hostile sheet must never crash book open —
    // degradation is semantic-tags-only rendering, per the PRD.
    return runCatching { CssParser.parse(bytes.decodeToString()) }
      .onFailure { Log.d(TAG, "stylesheet parse failed href=$href", it) }
      .getOrNull()
  }
}
