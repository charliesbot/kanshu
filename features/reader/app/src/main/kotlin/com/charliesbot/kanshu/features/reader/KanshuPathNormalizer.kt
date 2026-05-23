package com.charliesbot.kanshu.features.reader

import java.net.URLDecoder

object KanshuPathNormalizer {
  /**
   * Normalizes the path requested by WebView, ensuring no directory traversal, cleaning relative
   * segments, collapsing slashes, and removing leading slash. Returns null if any traversal check
   * fails or if the path is invalid.
   */
  fun normalizeAndRejectTraversal(path: String): String? {
    // Percent-decode once
    val decoded = runCatching { URLDecoder.decode(path, "UTF-8") }.getOrNull() ?: return null

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
      if (segment.isEmpty() || segment == ".") continue
      if (segment == "..") {
        if (resolved.isNotEmpty()) {
          resolved.removeAt(resolved.lastIndex)
        } else {
          return null // Attempt to traverse beyond root
        }
        continue
      }
      resolved.add(segment)
    }

    // Reconstruct cleaned path and strip any leading slash
    val clean = resolved.joinToString("/").trimStart('/')
    if (clean.isEmpty()) return null
    return clean
  }
}
