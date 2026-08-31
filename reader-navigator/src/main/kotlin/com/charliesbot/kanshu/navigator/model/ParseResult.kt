package com.charliesbot.kanshu.navigator.model

data class ParseDiagnostics(
  val unsupportedBlockTags: Map<String, Int> = emptyMap(),
  val unsupportedInlineTags: Map<String, Int> = emptyMap(),
  val stylingCensus: StylingCensus = StylingCensus(),
)

/**
 * Measurement of how a spine item expresses styling — the admission mechanism for the publisher
 * styles property allowlist. See docs/design/publisher-styles-engine.md § Diagnostics.
 */
data class StylingCensus(
  val classAttributeCount: Int = 0,
  val styleAttributeCount: Int = 0,
  val classNameCounts: Map<String, Int> = emptyMap(),
  val inlinePropertyCounts: Map<String, Int> = emptyMap(),
  val stylesheetHrefs: List<String> = emptyList(),
  val styleTagCount: Int = 0,
  /** Declarations seen per property across the spine item's parsed stylesheets. */
  val stylesheetPropertyCounts: Map<String, Int> = emptyMap(),
  val unsupportedSelectorCount: Int = 0,
  val atRuleCounts: Map<String, Int> = emptyMap(),
  val importantCount: Int = 0,
  /** Computed block-styled inline elements grouped by element/class and semantic block owner. */
  val blockDisplayContextCounts: Map<String, Int> = emptyMap(),
)

data class ParseResult(val document: ReaderDocument, val diagnostics: ParseDiagnostics)
