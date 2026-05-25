package com.charliesbot.kanshu.navigator.model

data class ParseDiagnostics(
  val unsupportedBlockTags: Map<String, Int> = emptyMap(),
  val unsupportedInlineTags: Map<String, Int> = emptyMap(),
)

data class ParseResult(val document: ReaderDocument, val diagnostics: ParseDiagnostics)
