package com.charliesbot.kanshu.core.reader

import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode

// Single source of truth for reader typography and pagination. Kept opinionated and small in
// V1: one serif, black on white, justified paragraphs, capped line length, generous side
// margin. Per the PRD, publisher styling is replaced wholesale — Kindle-style — so e-ink stays
// high contrast and the reading surface doesn't drift book to book. User-controlled font /
// size / weight is later.
//
// Pagination uses CSS multi-column layout sized to the viewport: each column is one paginated
// page. The host element overflows horizontally and we move between pages by translating it,
// so the WebView never scrolls (scrolling on e-ink ghosts and looks janky).
private val INJECTED_STYLESHEET =
  """
  html, body {
    margin: 0;
    padding: 0;
    height: 100vh;
    width: 100vw;
    overflow: hidden;
    background: #ffffff;
    color: #000000;
  }
  #kanshu-page-host {
    position: absolute;
    top: 0;
    left: 0;
    height: 100vh;
    padding: 24px;
    box-sizing: border-box;
    column-width: calc(100vw - 48px);
    -webkit-column-width: calc(100vw - 48px);
    column-gap: 48px;
    -webkit-column-gap: 48px;
    column-fill: auto;
    font-family: serif;
    font-size: 18px;
    line-height: 1.55;
    hyphens: auto;
    -webkit-text-size-adjust: 100%;
    transform: translateX(0);
    transition: none;
    will-change: transform;
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

// JS runs inside the WebView. It reports the page count back through the `Kanshu` JS interface
// and exposes `kanshuGoToPage(i)` so Kotlin can move between pages without scroll. Pages are
// turned by translating the host left by `i * innerWidth`; html/body have `overflow: hidden`
// so columns past the viewport are clipped instead of scrollable.
private val INJECTED_SCRIPT =
  """
  (function() {
    function reportPageCount() {
      var host = document.getElementById('kanshu-page-host');
      if (!host || !window.Kanshu || !Kanshu.onPageCount) return;
      var count = Math.ceil(host.scrollWidth / window.innerWidth);
      if (count < 1) count = 1;
      Kanshu.onPageCount(count);
    }
    window.kanshuGoToPage = function(i) {
      var host = document.getElementById('kanshu-page-host');
      if (!host) return;
      host.style.transform = 'translateX(' + (-i * window.innerWidth) + 'px)';
    };
    if (document.readyState === 'complete' || document.readyState === 'interactive') {
      reportPageCount();
    } else {
      document.addEventListener('DOMContentLoaded', reportPageCount);
    }
    window.addEventListener('load', reportPageCount);
    window.addEventListener('resize', reportPageCount);
  })();
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
    // Use DataNode (not appendText/TextNode) so JSoup serializes script/style content verbatim.
    // appendText escapes `<` to `&lt;`, which mangles the JS and produces a SyntaxError at the
    // first comparison.
    doc.head().appendElement("style").appendChild(DataNode(INJECTED_STYLESHEET))

    // Move every body child (text + element nodes) into the page host so the multi-column
    // layout has a single positioned ancestor to grow under. The script runs after the host so
    // getElementById finds it on first execution.
    val body = doc.body()
    val host = doc.createElement("div").attr("id", "kanshu-page-host")
    while (body.childNodeSize() > 0) host.appendChild(body.childNode(0))
    body.appendChild(host)
    body.appendElement("script").appendChild(DataNode(INJECTED_SCRIPT))
    return doc.outerHtml()
  }
}
