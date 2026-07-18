package com.charliesbot.kanshu.navigator.model

/**
 * Publisher structural spacing resolved at parse time, in em of the block's body text. Null fields
 * mean "no publisher signal." A null [BlockSpacing] on a block means the publisher declared no
 * spacing at all — the renderer applies the unstyled-book fallback convention (first-line indent,
 * no vertical gap). See docs/PRD_PUBLISHER_STYLES.md § Structural Spacing.
 */
data class BlockSpacing(
  val marginTopEm: Float? = null,
  val marginBottomEm: Float? = null,
  val marginStartEm: Float? = null,
  val marginEndEm: Float? = null,
  val textIndentEm: Float? = null,
)
