package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.ParseDiagnostics

internal class ParseDiagnosticsCollector {
  private val unsupportedBlockTags = mutableMapOf<String, Int>()
  private val unsupportedInlineTags = mutableMapOf<String, Int>()

  fun recordUnsupportedBlock(tagName: String) {
    increment(unsupportedBlockTags, tagName)
  }

  fun recordUnsupportedInline(tagName: String) {
    increment(unsupportedInlineTags, tagName)
  }

  fun build(): ParseDiagnostics =
    ParseDiagnostics(
      unsupportedBlockTags = unsupportedBlockTags.toMap(),
      unsupportedInlineTags = unsupportedInlineTags.toMap(),
    )

  private fun increment(tags: MutableMap<String, Int>, tagName: String) {
    val tag = tagName.lowercase()
    tags[tag] = tags.getOrDefault(tag, 0) + 1
  }
}
