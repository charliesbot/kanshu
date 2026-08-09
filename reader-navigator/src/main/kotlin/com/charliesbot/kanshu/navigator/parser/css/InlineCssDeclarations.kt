package com.charliesbot.kanshu.navigator.parser.css

internal data class InlineCssDeclaration(val property: String, val value: String)

/** Parses a `style=""` declaration list while tolerating malformed segments. */
internal fun parseInlineDeclarations(style: String): List<InlineCssDeclaration> =
  style.split(';').mapNotNull { segment ->
    if (!segment.contains(':')) return@mapNotNull null
    val property = segment.substringBefore(':').trim().lowercase()
    if (property.isEmpty()) return@mapNotNull null
    InlineCssDeclaration(
      property = property,
      value = segment.substringAfter(':').trim(),
    )
  }
