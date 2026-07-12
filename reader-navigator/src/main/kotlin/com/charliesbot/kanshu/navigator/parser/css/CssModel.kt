package com.charliesbot.kanshu.navigator.parser.css

/**
 * A parsed stylesheet: the rules Kanshu can honor plus a census of everything it skipped. Parsed
 * once per stylesheet and cached per publication; see docs/PRD_PUBLISHER_STYLES.md.
 */
data class CssStylesheet(
  val rules: List<CssRule> = emptyList(),
  val stats: CssParseStats = CssParseStats(),
)

data class CssRule(val selector: CssSelector, val declarations: List<CssDeclaration>)

/** Compounds in document order; a descendant combinator is implied between adjacent compounds. */
data class CssSelector(val compounds: List<CssCompound>) {
  /** Standard (id, class, type) specificity, packed for comparison. */
  val specificity: Int = compounds.sumOf { compound ->
    (if (compound.id != null) 10_000 else 0) +
      compound.classes.size * 100 +
      (if (compound.type != null) 1 else 0)
  }
}

data class CssCompound(
  val type: String? = null,
  val id: String? = null,
  val classes: Set<String> = emptySet(),
)

data class CssDeclaration(val property: String, val value: String)

/** What the parser skipped or merely observed — feeds the styling census. */
data class CssParseStats(
  val declarationCounts: Map<String, Int> = emptyMap(),
  val unsupportedSelectorCount: Int = 0,
  val atRuleCounts: Map<String, Int> = emptyMap(),
  val importantCount: Int = 0,
)

/** Properties the micro-cascade honors. Growth is census-gated; see the PRD. */
internal val ALLOWLISTED_PROPERTIES = setOf("font-style", "font-weight", "text-align")
