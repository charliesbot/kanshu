package com.charliesbot.kanshu.features.reader

import android.util.Log
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object KanshuHtmlSanitizer {
  private const val TAG = "KanshuHtmlSanitizer"

  private val ALLOWED_TAGS =
    setOf(
      "p",
      "div",
      "section",
      "article",
      "aside",
      "header",
      "footer",
      "nav",
      "blockquote",
      "figure",
      "figcaption",
      "br",
      "hr",
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "ul",
      "ol",
      "li",
      "dl",
      "dt",
      "dd",
      "span",
      "a",
      "em",
      "strong",
      "i",
      "b",
      "u",
      "s",
      "sub",
      "sup",
      "code",
      "pre",
      "mark",
      "small",
      "cite",
      "q",
      "dfn",
      "abbr",
      "time",
      "address",
      "bdi",
      "bdo",
      "ruby",
      "rt",
      "rp",
      "img",
      "svg",
      "path",
      "rect",
      "circle",
      "ellipse",
      "line",
      "polyline",
      "polygon",
      "g",
      "defs",
      "use",
      "symbol",
      "linearGradient",
      "radialGradient",
      "lineargradient",
      "radialgradient",
      "clipPath",
      "clippath",
      "mask",
      "filter",
      "pattern",
      "image",
      "stop",
      "text",
      "tspan",
      "table",
      "thead",
      "tbody",
      "tfoot",
      "tr",
      "td",
      "th",
      "caption",
    )

  private val ALLOWED_SVG_TAGS =
    setOf(
      "svg",
      "path",
      "rect",
      "circle",
      "ellipse",
      "line",
      "polyline",
      "polygon",
      "g",
      "defs",
      "use",
      "symbol",
      "linearGradient",
      "radialGradient",
      "lineargradient",
      "radialgradient",
      "clipPath",
      "clippath",
      "mask",
      "filter",
      "pattern",
      "image",
      "stop",
      "text",
      "tspan",
    )

  private val STRUCTURAL_TAGS = setOf("html", "head", "body", "meta", "title", "link", "style")

  /**
   * Sanitizes the publisher HTML document, applying structural and semantic whitelisting, CSS
   * declaration-level scrubbing, visual error badging for unrecognized tags, and wraps it inside
   * the trusted Kanshu reader HTML shell.
   */
  fun sanitizeAndWrap(
    rawHtml: String,
    loadId: Int = 0,
    targetPageIndex: Int = 0,
    prefs: ReaderPreferences = ReaderPreferences(),
  ): String {
    val doc = Jsoup.parse(rawHtml)

    // First pass: Walk the DOM tree and clean/badge elements in place
    cleanElement(doc.root())

    // Second pass: Extract head links and style blocks, then wrap inside the Kanshu shell
    val shellDoc = Document.createShell("https://kanshu.invalid/")

    // Setup head meta
    val head = shellDoc.head()
    head.appendElement("meta").attr("charset", "utf-8")
    head.appendElement("meta").apply {
      attr("name", "viewport")
      attr("content", "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no")
    }

    // Copy cleaned publisher stylesheets and style tags FIRST
    doc.head().children().forEach { child ->
      val tagName = child.tagName()
      if (tagName == "link" && child.attr("rel") == "stylesheet") {
        head.appendChild(child.clone())
      } else if (tagName == "style") {
        head.appendChild(child.clone())
      }
    }

    // Inject initial preferences variables directly in <style> block
    head.appendElement("style").apply {
      val multiplier = prefs.margins.value
      val cssContent =
        """
        :root {
          --reader-font: "${prefs.font.name}-Kanshu";
          --font-size: ${(18 * prefs.fontScale).toInt()}px;
          --line-height: ${prefs.lineSpacing};
          --text-align: ${prefs.alignment.name.lowercase()};
          --page-margin-inline: ${(24 * multiplier).toInt()}px;
          --page-margin-block: ${(32 * multiplier).toInt()}px;
          --paragraph-spacing: ${prefs.paragraphSpacing}em;
          --word-spacing: ${prefs.wordSpacing}em;
          --letter-spacing: ${prefs.letterSpacing}em;
        }
      """
          .trimIndent()
      appendChild(DataNode(cssContent))
    }

    // Inject Kanshu core stylesheet LAST so it has higher specificity/override priority
    head.appendElement("link").apply {
      attr("rel", "stylesheet")
      attr("href", "https://kanshu.invalid/__kanshu__/kanshu-reader.css")
    }

    // Create the page container main and append all cleaned body children
    val body = shellDoc.body()
    val pageContainer = body.appendElement("main").attr("id", "kanshu-page")

    doc.body().children().forEach { child -> pageContainer.appendChild(child.clone()) }

    // Inject JS bridge script at the bottom of the body
    body.appendElement("script").apply {
      attr("src", "https://kanshu.invalid/__kanshu__/kanshu-reader.js")
    }
    body.appendElement("script").apply {
      val jsContent =
        """
        window.__kanshuChapterLoadId__ = $loadId;
        window.addEventListener('DOMContentLoaded', () => {
          window.kanshu.repaginate(0, $targetPageIndex);
        });
      """
          .trimIndent()
      appendChild(DataNode(jsContent))
    }

    return "<!DOCTYPE html>\n" + shellDoc.outerHtml()
  }

  private fun cleanElement(element: Element) {
    if (cleanRootOrDocument(element)) return
    if (cleanLinkElement(element)) return
    if (cleanStyleElement(element)) return
    if (cleanStructuralElement(element)) return
    if (cleanUnrecognizedElement(element)) return

    cleanElementAttributes(element)

    // Recurse on children of whitelisted element
    element.children().toList().forEach { cleanElement(it) }
  }

  private fun cleanRootOrDocument(element: Element): Boolean {
    if (element is Document || element.tagName() == "#root") {
      element.children().toList().forEach { cleanElement(it) }
      return true
    }
    return false
  }

  private fun cleanLinkElement(element: Element): Boolean {
    if (element.tagName() == "link") {
      val rel = element.attr("rel").lowercase()
      val href = element.attr("href")
      if (rel != "stylesheet" || isUnsafeUrl(href)) {
        element.remove()
      }
      return true
    }
    return false
  }

  private fun cleanStyleElement(element: Element): Boolean {
    if (element.tagName() == "style") {
      val css = element.data()
      element.empty().appendChild(DataNode(sanitizeCss(css)))
      return true
    }
    return false
  }

  private fun cleanStructuralElement(element: Element): Boolean {
    if (element.tagName() in STRUCTURAL_TAGS) {
      element.children().toList().forEach { cleanElement(it) }
      return true
    }
    return false
  }

  private fun cleanUnrecognizedElement(element: Element): Boolean {
    val tagName = element.tagName()
    if (tagName !in ALLOWED_TAGS) {
      Log.w(TAG, "Sanitizer stripped unrecognized tag: <$tagName>")

      // Silent cleanup inside SVG to prevent visual badging from corrupting coordinates
      val isInsideSvg = element.parents().any { it.tagName() == "svg" }
      if (isInsideSvg) {
        element.remove()
        return true
      }

      // Create red visual error badge
      val debugBox = element.ownerDocument()?.createElement("span") ?: Element(tagName)
      debugBox.attr(
        "style",
        "border: 1px dashed #d32f2f; background: #ffebee; color: #d32f2f; padding: 2px 4px; margin: 0 4px; font-family: monospace; font-size: 11px; border-radius: 3px; display: inline-block;",
      )
      debugBox.text("[Error: Missing Tag <$tagName>] ")

      // Move children into the badge to preserve inner content readability
      val childNodes = element.childNodes().toList()
      for (child in childNodes) {
        child.remove()
        debugBox.appendChild(child)
      }

      // Replace the unrecognized element in the DOM
      element.replaceWith(debugBox)

      // Recurse on children inside the new debug badge
      debugBox.children().forEach { cleanElement(it) }
      return true
    }
    return false
  }

  private fun cleanElementAttributes(element: Element) {
    val tagName = element.tagName()
    val attributes = element.attributes().toList()
    for (attr in attributes) {
      val attrName = attr.key.lowercase()
      val attrVal = attr.value

      // Remove any event handlers
      if (attrName.startsWith("on")) {
        element.removeAttr(attr.key)
        continue
      }

      when (tagName) {
        "img" -> {
          if (attrName !in setOf("src", "alt", "title", "width", "height", "style")) {
            element.removeAttr(attr.key)
          } else if (attrName == "src" && isUnsafeUrl(attrVal)) {
            element.removeAttr(attr.key)
          } else if (attrName == "style") {
            element.attr(attr.key, sanitizeCssDeclarations(attrVal))
          }
        }
        "a" -> {
          if (attrName != "href") {
            element.removeAttr(attr.key)
          } else if (isUnsafeUrl(attrVal)) {
            element.removeAttr(attr.key)
          }
        }
        in ALLOWED_SVG_TAGS -> {
          if (attrName == "style") {
            element.attr(attr.key, sanitizeCssDeclarations(attrVal))
          } else if (attrName == "href" || attrName == "xlink:href") {
            if (isUnsafeUrl(attrVal)) {
              element.removeAttr(attr.key)
            }
          }
        }
        else -> {
          if (attrName == "style") {
            element.attr(attr.key, sanitizeCssDeclarations(attrVal))
          } else if (attrName != "class" && attrName != "id") {
            // Keep style, class, and id for generic allowed tags
            element.removeAttr(attr.key)
          }
        }
      }
    }
  }

  private data class CssBlock(val selector: String, val body: String)

  private object CssBlockParser {
    fun parse(css: String): List<CssBlock> {
      val blocks = mutableListOf<CssBlock>()
      var braceDepth = 0
      var blockStart = 0
      var selectorStart = 0

      for (i in css.indices) {
        val c = css[i]
        if (c == '{') {
          if (braceDepth == 0) {
            blockStart = i
          }
          braceDepth++
        } else if (c == '}') {
          braceDepth--
          if (braceDepth == 0) {
            val selector = css.substring(selectorStart, blockStart).trim()
            val body = css.substring(blockStart + 1, i)
            blocks.add(CssBlock(selector, body))
            selectorStart = i + 1
          } else if (braceDepth < 0) {
            braceDepth = 0
            selectorStart = i + 1
          }
        }
      }
      return blocks
    }
  }

  internal fun sanitizeCss(css: String): String {
    val blocks = CssBlockParser.parse(css)
    val sb = StringBuilder()

    for (block in blocks) {
      val selector = block.selector
      val lowerSelector = selector.lowercase()

      if (lowerSelector.startsWith("@import")) {
        continue
      }

      if (lowerSelector.startsWith("@media") || lowerSelector.startsWith("@supports")) {
        val sanitizedBody = sanitizeCss(block.body)
        sb.append(selector).append(" {\n").append(sanitizedBody).append("}\n")
      } else {
        val sanitizedDeclarations = sanitizeCssDeclarations(block.body)
        sb.append(selector).append(" { ").append(sanitizedDeclarations).append(" }\n")
      }
    }

    return sb.toString()
  }

  internal fun sanitizeCssDeclarations(declarations: String): String {
    return declarations
      .split(';')
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .filter { dec ->
        val lower = dec.lowercase()
        !lower.contains("@import") &&
          !lower.contains("expression(") &&
          !lower.contains("behavior:") &&
          !hasUnsafeUrl(lower)
      }
      .joinToString("; ")
  }

  private fun hasUnsafeUrl(value: String): Boolean {
    val regex = Regex("""url\s*\(\s*['"]?([^'")]+)['"]?\s*\)""")
    val matches = regex.findAll(value)
    for (match in matches) {
      val url = match.groupValues[1].trim()
      if (isUnsafeUrl(url)) {
        return true
      }
    }
    return false
  }

  private fun isUnsafeUrl(url: String): Boolean {
    val lower = url.trim().lowercase()
    if (lower.startsWith("//")) return true

    val schemeIndex = lower.indexOf(':')
    if (schemeIndex != -1) {
      val scheme = lower.substring(0, schemeIndex)
      if (scheme == "http" || scheme == "https") {
        return !lower.contains("kanshu.invalid")
      }
      return true
    }

    return false
  }
}
