package com.charliesbot.kanshu.core.reader

/** The five highlight colors exposed by the reader's Kindle-style palette. */
enum class ReaderHighlightColor(val key: String, val argb: Long) {
  Aqua("AQUA", 0xFF6ADADC),
  Pink("PINK", 0xFFF6AEC4),
  Orange("ORANGE", 0xFFF7B96C),
  Yellow("YELLOW", 0xFFFAE06F),
  Green("GREEN", 0xFF69D3A7);

  companion object {
    val default: ReaderHighlightColor = Yellow

    fun fromStorageValue(value: String): ReaderHighlightColor =
      entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: default
  }
}
