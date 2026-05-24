package com.charliesbot.kanshu.features.reader

import java.io.ByteArrayOutputStream

object KanshuPathNormalizer {
  /**
   * Normalizes the path requested by WebView, ensuring no directory traversal, cleaning relative
   * segments, collapsing slashes, and removing leading slash. Returns null if any traversal check
   * fails or if the path is invalid.
   */
  fun normalizeAndRejectTraversal(path: String): String? {
    val lowerRaw = path.lowercase()
    if (lowerRaw.contains("%2f") || lowerRaw.contains("%2e")) {
      return null
    }

    // Custom percent-decode once (preserves '+')
    val decoded = percentDecode(path)
    val lowerDecoded = decoded.lowercase()
    if (lowerDecoded.contains("%2f") || lowerDecoded.contains("%2e")) {
      return null
    }

    // Reject backslashes, null bytes, and control characters
    if (decoded.contains('\\') || decoded.contains('\u0000')) return null
    for (char in decoded) {
      if (char.isISOControl()) return null
    }

    // Collapse repeated '/'
    val collapsed = decoded.replace(Regex("/{2,}"), "/")

    // Split segments and resolve relative '.' and '..' segments
    val segments = collapsed.split('/')
    val resolved = mutableListOf<String>()
    for (segment in segments) {
      val trimmed = segment.trim()
      if (trimmed.isEmpty() || trimmed == ".") continue

      if (trimmed == "..") {
        if (resolved.isNotEmpty()) {
          resolved.removeAt(resolved.lastIndex)
        } else {
          return null // Attempt to traverse beyond root
        }
        continue
      }

      val lowerSegment = trimmed.lowercase()
      if (lowerSegment.contains("%2e") || lowerSegment.contains("%2f")) {
        return null
      }

      resolved.add(trimmed)
    }

    // Reconstruct cleaned path and strip any leading slash
    val clean = resolved.joinToString("/").trimStart('/')
    if (clean.isEmpty()) return null
    return clean
  }

  /** Decodes percent-encoded character sequences (%XX) without converting '+' to space. */
  private fun percentDecode(input: String): String {
    val bos = ByteArrayOutputStream()
    var i = 0
    while (i < input.length) {
      val c = input[i]
      if (c == '%' && i + 2 < input.length) {
        val hex1 = Character.digit(input[i + 1], 16)
        val hex2 = Character.digit(input[i + 2], 16)
        if (hex1 != -1 && hex2 != -1) {
          bos.write((hex1 shl 4) + hex2)
          i += 3
          continue
        }
      }
      bos.write(c.code)
      i++
    }
    return bos.toString("UTF-8")
  }
}
