package com.charliesbot.kanshu.core.reader

import org.jsoup.Jsoup

// Single source of truth for reader typography. Kept opinionated and small in V1: one serif,
// black on white, justified paragraphs, generous side margin, capped line length. Per the PRD,
// publisher styling is replaced wholesale — Kindle-style — so e-ink stays high contrast and the
// reading surface doesn't drift book to book. User-controlled font / size / weight is later.
private val INJECTED_STYLESHEET =
  """
  html, body { background: #ffffff; color: #000000; margin: 0; padding: 0; }
  body {
    font-family: serif;
    font-size: 18px;
    line-height: 1.55;
    max-width: 36em;
    margin: 0 auto;
    padding: 24px;
    hyphens: auto;
    -webkit-text-size-adjust: 100%;
  }
  p { margin: 0 0 1em 0; text-align: justify; }
  h1, h2, h3, h4 { font-weight: bold; margin: 1.4em 0 0.6em 0; line-height: 1.25; }
  h1 { font-size: 1.6em; }
  h2 { font-size: 1.35em; }
  h3 { font-size: 1.15em; }
  em, i { font-style: italic; }
  strong, b { font-weight: bold; }
  blockquote { margin: 1em 1.5em; }
  img { max-width: 100%; height: auto; display: block; margin: 1em auto; }
  a { color: inherit; text-decoration: underline; }
  hr { border: 0; border-top: 1px solid #000000; margin: 1.5em auto; width: 50%; }
  """
    .trimIndent()

object ChapterStyler {
  fun style(rawHtml: ByteArray): String {
    val doc = Jsoup.parse(String(rawHtml, Charsets.UTF_8))
    // Match rel="stylesheet" / "STYLESHEET" / "alternate stylesheet" — Jsoup's default attribute
    // value selector is case-sensitive, so we filter the rel value ourselves.
    doc
      .select("link")
      .filter { it.attr("rel").contains("stylesheet", ignoreCase = true) }
      .forEach { it.remove() }
    doc.select("style").remove()
    // Strip inline style attributes only. Class and id stay so in-document anchors keep working
    // and a future stylesheet can hook on semantic classes if we choose to.
    doc.select("[style]").forEach { it.removeAttr("style") }
    doc.head().appendElement("style").appendText(INJECTED_STYLESHEET)
    return doc.outerHtml()
  }
}
