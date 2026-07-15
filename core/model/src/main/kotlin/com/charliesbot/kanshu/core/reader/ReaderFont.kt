package com.charliesbot.kanshu.core.reader

// Fonts bundled under features/reader/app/src/main/assets/fonts/. Each entry corresponds to a
// `FontFamily` declared in EpubTypography.fragmentConfiguration; the storage value is the enum
// name. The display name is what the Font tab in the reader prefs bottom sheet shows under each
// "Aa" chip, and the asset path is what the chip's preview loads at runtime so users see the
// actual face before selecting it. See
// features/reader/app/src/main/assets/fonts/_HOW_TO_ADD_FONTS.txt
// for the three-step convention when adding a new font.
enum class ReaderFont(
  val displayName: String,
  val regularAssetPath: String,
  // Static fonts can't ride the wght axis; they flip to a bold file when boldness is on.
  val boldAssetPath: String? = null,
  // Upper end of the font's wght axis (from its fvar table) — boldness steps scale into it so
  // no slider step is ever a dead tap.
  val maxFontWeight: Int = 900,
) {
  Literata("Literata", "fonts/Literata-VariableFont_opsz,wght.ttf"),
  Bitter("Bitter", "fonts/Bitter-VariableFont_wght.ttf"),
  LibreBaskerville(
    "Libre Baskerville",
    "fonts/LibreBaskerville-VariableFont_wght.ttf",
    maxFontWeight = 700,
  ),
  OpenDyslexic("OpenDyslexic", "fonts/OpenDyslexic-Regular.otf", "fonts/OpenDyslexic-Bold.otf"),
}
