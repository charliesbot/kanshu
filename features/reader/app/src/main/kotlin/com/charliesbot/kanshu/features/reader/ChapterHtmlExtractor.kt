package com.charliesbot.kanshu.features.reader

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

internal object ChapterHtmlExtractor {
  private val safelist =
    Safelist.relaxed()
      .addTags("section", "article", "aside", "figure", "figcaption")
      .addAttributes(":all", "class")
      .removeTags("img")

  fun bodyHtml(rawHtml: String): String {
    val document = Jsoup.parse(rawHtml)
    return Jsoup.clean(document.body().html(), safelist)
  }

  fun hasReadableText(bodyHtml: String): Boolean =
    Jsoup.parseBodyFragment(bodyHtml).text().isNotBlank()
}
