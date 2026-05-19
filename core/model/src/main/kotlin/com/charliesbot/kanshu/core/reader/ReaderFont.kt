package com.charliesbot.kanshu.core.reader

// Fonts bundled under features/reader/app/src/main/assets/fonts/. Each entry corresponds to a
// `FontFamily` declared in EpubTypography.fragmentConfiguration; the storage value is the enum
// name. The display name is what the Font tab in the reader prefs bottom sheet shows under each
// "Aa" chip. See features/reader/app/src/main/assets/fonts/_HOW_TO_ADD_FONTS.txt for the
// three-step convention when adding a new font.
enum class ReaderFont(val displayName: String) {
  NotoSerif("Noto Serif"),
  Inter("Inter"),
  Bitter("Bitter"),
  LibreBaskerville("Libre Baskerville"),
  Literata("Literata"),
  OpenDyslexic("OpenDyslexic"),
}
