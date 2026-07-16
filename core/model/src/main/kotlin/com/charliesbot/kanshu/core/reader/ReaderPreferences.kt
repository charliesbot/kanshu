package com.charliesbot.kanshu.core.reader

// User-controlled reader preferences. `fontScale` is a multiplier on the base font size (1.0 =
// 100% of the publisher / default size). The valid range is enforced by ReaderPreferences.SCALE_*
// constants and clamped when persisted; values outside the range are not meaningful in the UI
// (the slider can't reach them) but the clamp guards against stale stored values.
data class ReaderPreferences(
  val font: ReaderFont = ReaderFont.Literata,
  val fontScale: Float = SCALE_DEFAULT,
  // Weight boost on top of the font's natural weight, 0 = unchanged. Variable fonts ride the
  // wght axis (400..700); static fonts flip to their bold file.
  val boldness: Float = BOLDNESS_DEFAULT,
  val margins: ReaderMargins = ReaderMargins.Medium,
  val alignment: ReaderAlignment = ReaderAlignment.Justify,
  val lineSpacing: Float = LINE_SPACING_DEFAULT,
  val paragraphSpacing: Float = PARAGRAPH_SPACING_DEFAULT,
  val wordSpacing: Float = WORD_SPACING_DEFAULT,
  val letterSpacing: Float = LETTER_SPACING_DEFAULT,
) {
  companion object {
    // Floor calibrated against Kindle: its smallest size matches ~0.8x of our 18sp base, and
    // anything below is unreadable dead range. 0.8..2.1 in 0.1 steps = 14 stops, Kindle parity.
    const val SCALE_MIN: Float = 0.8f
    const val SCALE_MAX: Float = 2.1f
    const val SCALE_DEFAULT: Float = 1.0f
    const val SCALE_STEP: Float = 0.1f
    const val BOLDNESS_MIN: Float = 0f
    const val BOLDNESS_MAX: Float = 0.5f
    const val BOLDNESS_DEFAULT: Float = 0f
    const val BOLDNESS_STEP: Float = 0.1f

    const val LINE_SPACING_MIN: Float = 1.0f
    const val LINE_SPACING_MAX: Float = 1.8f
    const val LINE_SPACING_DEFAULT: Float = 1.4f
    const val LINE_SPACING_STEP: Float = 0.2f

    const val PARAGRAPH_SPACING_MIN: Float = 0.0f
    const val PARAGRAPH_SPACING_MAX: Float = 2.0f
    // Additive over publisher structural spacing (the Kindle model): books carry their own
    // vertical rhythm via CSS margins, and unstyled paragraphs separate by first-line indent, so
    // the reader adds nothing by default. See docs/PRD_PUBLISHER_STYLES.md § Structural Spacing.
    const val PARAGRAPH_SPACING_DEFAULT: Float = 0.0f
    const val PARAGRAPH_SPACING_STEP: Float = 0.5f

    const val WORD_SPACING_MIN: Float = 0.0f
    const val WORD_SPACING_MAX: Float = 0.4f
    const val WORD_SPACING_DEFAULT: Float = 0.0f
    const val WORD_SPACING_STEP: Float = 0.1f

    const val LETTER_SPACING_MIN: Float = 0.0f
    const val LETTER_SPACING_MAX: Float = 0.2f
    const val LETTER_SPACING_DEFAULT: Float = 0.0f
    const val LETTER_SPACING_STEP: Float = 0.05f
  }
}
