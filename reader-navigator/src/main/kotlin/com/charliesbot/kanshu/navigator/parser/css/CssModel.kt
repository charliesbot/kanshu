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
internal val ALLOWLISTED_PROPERTIES =
  setOf(
    "font-style",
    "font-weight",
    "text-align",
    "margin",
    "margin-top",
    "margin-bottom",
    "margin-left",
    "margin-right",
    "text-indent",
  )

/**
 * Normalizes shorthand into the longhand declarations the resolver understands. `margin` expands
 * per the CSS 1-4 value pattern; everything else passes through. Downstream code only ever sees
 * longhands.
 */
internal fun expandCssDeclaration(property: String, value: String): List<CssDeclaration> {
  if (property != "margin") return listOf(CssDeclaration(property, value))
  val parts = value.split(CSS_VALUE_WHITESPACE).filter { it.isNotEmpty() }
  val (top, right, bottom, left) =
    when (parts.size) {
      1 -> listOf(parts[0], parts[0], parts[0], parts[0])
      2 -> listOf(parts[0], parts[1], parts[0], parts[1])
      3 -> listOf(parts[0], parts[1], parts[2], parts[1])
      4 -> parts
      else -> return emptyList()
    }
  return listOf(
    CssDeclaration("margin-top", top),
    CssDeclaration("margin-right", right),
    CssDeclaration("margin-bottom", bottom),
    CssDeclaration("margin-left", left),
  )
}

private val CSS_VALUE_WHITESPACE = Regex("""\s+""")

/**
 * A CSS length normalized to em, or null for values the cascade treats as "no signal" (`auto`,
 * percentages, negatives, unparseable). Nominal ratios: 1em = 16px = 12pt. Clamping to the
 * per-property ranges happens where the value is applied; see docs/PRD_PUBLISHER_STYLES.md §
 * Structural Spacing.
 */
internal fun parseCssLengthToEm(value: String): Float? {
  val trimmed = value.trim().lowercase()
  if (trimmed == "0") return 0f
  val match = CSS_LENGTH_PATTERN.matchEntire(trimmed) ?: return null
  val number = match.groupValues[1].toFloatOrNull() ?: return null
  if (number < 0f) return null
  return when (match.groupValues[2]) {
    "em",
    "rem" -> number
    "px" -> number / 16f
    "pt" -> number / 12f
    else -> null
  }
}

private val CSS_LENGTH_PATTERN = Regex("""(-?\d*\.?\d+)([a-z%]+)""")
