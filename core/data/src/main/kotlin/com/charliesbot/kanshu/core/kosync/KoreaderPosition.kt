package com.charliesbot.kanshu.core.kosync

// Pure-string utilities for the kosync `progress` field. The wire format Kavita exchanges with
// KOReader-compatible clients is `/body/DocFragment[N]/body/<xpath>[.<charOffset>]`, where N is
// 1-indexed and corresponds to the EPUB spine position.
//
// v0 syncs at spine precision only. Within-chunk position (the `<xpath>` tail) is intentionally
// not produced here — Readium emits progression-style locators rather than positional XPaths
// for ongoing reading, and Kavita drops character offsets on storage anyway, so the cross-device
// ceiling is "element-level inside a chunk." We document this in docs/KAVITA_API.md and revisit
// if real-world handoff feels too coarse.
//
// The encoder always writes spine-top (`.0`). The decoder accepts every shape Kavita can return
// (full XPath, short `/body/DocFragment[N]`, `.0`-truncated, hash-fragment `#_doc_fragment_N`)
// and yields the spine index regardless of within-chunk detail.
object KoreaderPosition {

  // 1-indexed → 0-indexed mapping mirrors how Kavita stores `pageNum` (see KoreaderHelper.cs).
  private val DOC_FRAGMENT = Regex("DocFragment\\[(\\d+)\\]")
  private val HASH_FRAGMENT = Regex("^#_doc_fragment_?(\\d+)")

  fun encode(spineIndex: Int): String {
    require(spineIndex >= 0) { "spineIndex must be non-negative" }
    return "/body/DocFragment[${spineIndex + 1}].0"
  }

  // Returns null when the XPointer carries no decodable spine reference — including the
  // numeric-only form Kavita sends for PDF/archive progress, which we don't handle here.
  fun decodeSpineIndex(xpointer: String): Int? {
    if (xpointer.isBlank()) return null
    DOC_FRAGMENT.find(xpointer)?.let { match ->
      val n = match.groupValues[1].toIntOrNull() ?: return null
      return (n - 1).takeIf { it >= 0 }
    }
    HASH_FRAGMENT.find(xpointer)?.let { match ->
      val n = match.groupValues[1].toIntOrNull() ?: return null
      return (n - 1).takeIf { it >= 0 }
    }
    return null
  }
}
