package com.charliesbot.kanshu.core.reader

// User-controlled reader preferences. `fontScale` is a multiplier on the base font size (1.0 =
// 100% of the publisher / default size). The valid range is enforced by ReaderPreferences.SCALE_*
// constants and clamped when persisted; values outside the range are not meaningful in the UI
// (the slider can't reach them) but the clamp guards against stale stored values.
data class ReaderPreferences(
  val font: ReaderFont = ReaderFont.Literata,
  val fontScale: Float = SCALE_DEFAULT,
) {
  companion object {
    const val SCALE_MIN: Float = 0.5f
    const val SCALE_MAX: Float = 2.0f
    const val SCALE_DEFAULT: Float = 1.0f
    const val SCALE_STEP: Float = 0.1f
  }
}
