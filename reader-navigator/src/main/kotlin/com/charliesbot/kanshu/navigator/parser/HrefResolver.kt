package com.charliesbot.kanshu.navigator.parser

private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

/**
 * Resolves a resource href from spine XHTML against the spine item's publication-root-relative
 * path, producing a publication-root-relative href.
 *
 * Pure path arithmetic so the parser stays free of Android and java.net dependencies. Hrefs with a
 * scheme (http:, data:) pass through untouched. Root-relative hrefs are treated as
 * publication-root-relative. Parent traversal above the root clamps at the root.
 */
internal fun resolveHref(href: String, baseHref: String?): String {
  if (baseHref.isNullOrBlank() || href.isBlank()) return href
  if (SCHEME_PREFIX.containsMatchIn(href)) return href
  if (href.startsWith("/")) return href.trimStart('/')

  val segments = ArrayDeque<String>()
  val baseDir = baseHref.trimStart('/').substringBeforeLast('/', "")
  if (baseDir.isNotEmpty()) {
    baseDir.split('/').forEach(segments::addLast)
  }
  href.split('/').forEach { segment ->
    when (segment) {
      "",
      "." -> {}
      ".." -> segments.removeLastOrNull()
      else -> segments.addLast(segment)
    }
  }
  return segments.joinToString("/")
}
