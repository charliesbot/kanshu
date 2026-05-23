package com.charliesbot.kanshu.features.reader

import android.util.Log
import org.jsoup.Jsoup
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
      "table",
      "thead",
      "tbody",
      "tfoot",
      "tr",
      "td",
      "th",
      "caption",
    )

  private val STRUCTURAL_TAGS = setOf("html", "head", "body", "meta", "title", "link", "style")

  /**
   * Sanitizes the publisher HTML document, applying structural and semantic whitelisting, CSS
   * declaration-level scrubbing, visual error badging for unrecognized tags, and wraps it inside
   * the trusted Kanshu reader HTML shell.
   */
  fun sanitizeAndWrap(rawHtml: String): String {
    val doc = Jsoup.parse(rawHtml)

    // First pass: Walk the DOM tree and clean/badge elements in place
    cleanElement(doc.root())

    // Second pass: Extract head links and style blocks, then wrap inside the Kanshu shell
    val shellDoc = Document.createShell("https://kanshu.invalid/")

    // Setup head meta and core stylesheet link
    val head = shellDoc.head()
    head.appendElement("meta").attr("charset", "utf-8")
    head.appendElement("meta").apply {
      attr("name", "viewport")
      attr("content", "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no")
    }
    head.appendElement("link").apply {
      attr("rel", "stylesheet")
      attr("href", "https://kanshu.invalid/__kanshu__/kanshu-reader.css")
    }

    // Copy cleaned publisher stylesheets and style tags
    doc.head().children().forEach { child ->
      val tagName = child.tagName()
      if (tagName == "link" && child.attr("rel") == "stylesheet") {
        head.appendChild(child.clone())
      } else if (tagName == "style") {
        head.appendChild(child.clone())
      }
    }

    // Create the page container div and append all cleaned body children
    val body = shellDoc.body()
    val pageContainer = body.appendElement("div").attr("id", "kanshu-page")

    doc.body().children().forEach { child -> pageContainer.appendChild(child.clone()) }

    return "<!DOCTYPE html>\n" + shellDoc.outerHtml()
  }

  private fun cleanElement(element: Element) {
    val tagName = element.tagName()

    if (element is Document || tagName == "#root") {
      val children = element.children().toList()
      children.forEach { cleanElement(it) }
      return
    }

    // 1. Process link tags in head
    if (tagName == "link") {
      val rel = element.attr("rel").lowercase()
      val href = element.attr("href")
      if (rel != "stylesheet" || isUnsafeUrl(href)) {
        element.remove()
      }
      return
    }

    // 2. Process style tags
    if (tagName == "style") {
      val css = element.data()
      element.text(sanitizeCss(css))
      return
    }

    // 3. Skip structural tags but clean their children
    if (tagName in STRUCTURAL_TAGS) {
      val children = element.children().toList()
      children.forEach { cleanElement(it) }
      return
    }

    // 4. Handle unrecognized tags: Transform them into a visual inline red warning badge
    if (tagName !in ALLOWED_TAGS) {
      Log.w(TAG, "Sanitizer stripped unrecognized tag: <$tagName>")

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
      return
    }

    // 5. Clean allowed elements: Filter and sanitize attributes
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
          if (attrName !in setOf("src", "alt", "title", "width", "height")) {
            element.removeAttr(attr.key)
          } else if (attrName == "src" && isUnsafeUrl(attrVal)) {
            element.removeAttr(attr.key)
          }
        }
        "a" -> {
          if (attrName != "href") {
            element.removeAttr(attr.key)
          } else if (isUnsafeUrl(attrVal)) {
            element.removeAttr(attr.key)
          }
        }
        else -> {
          if (attrName == "style") {
            element.attr(attr.key, sanitizeCssDeclarations(attrVal))
          } else if (attrName != "class" && attrName != "id") {
            // Keep only style, class, and id for generic allowed elements
            element.removeAttr(attr.key)
          }
        }
      }
    }

    // Clean children
    val children = element.children().toList()
    children.forEach { cleanElement(it) }
  }

  fun sanitizeCss(css: String): String {
    val sb = StringBuilder()
    var index = 0
    while (index < css.length) {
      val openBrace = css.indexOf('{', index)
      if (openBrace == -1) {
        sb.append(css.substring(index))
        break
      }
      val closeBrace = css.indexOf('}', openBrace)
      if (closeBrace == -1) {
        break // Strip rest on malformed CSS to fail safe
      }
      val selector = css.substring(index, openBrace)
      val declarations = css.substring(openBrace + 1, closeBrace)

      val cleanDeclarations = sanitizeCssDeclarations(declarations)
      sb.append(selector).append('{').append(cleanDeclarations).append('}')
      index = closeBrace + 1
    }
    return sb.toString()
  }

  fun sanitizeCssDeclarations(declarations: String): String {
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
    if (lower.startsWith("javascript:") || lower.startsWith("data:")) return true
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
      return !lower.contains("kanshu.invalid")
    }
    return false
  }
}
