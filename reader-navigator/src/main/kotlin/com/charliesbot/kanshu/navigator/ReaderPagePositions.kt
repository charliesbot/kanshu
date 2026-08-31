package com.charliesbot.kanshu.navigator

import com.charliesbot.kanshu.navigator.engine.ReaderLayoutResult

/**
 * Where each page of the current chapter starts in its flattened text stream.
 *
 * This is the reader's progress primitive. Page indexes are a navigation convenience that shifts
 * whenever font size, margins, or the pagination rules themselves change; character offsets do not,
 * so a position stored against one typography setting still resolves under another. See the
 * Progress Model in `docs/design/native-reader.md`.
 */
data class ReaderPagePositions(
  val pageStartCharOffsets: List<Int>,
  val textStreamLength: Int,
) {
  val pageCount: Int
    get() = pageStartCharOffsets.size

  /** Character offset the given page starts at; 0 when the page index is unknown. */
  fun charOffsetOf(pageIndex: Int): Int = pageStartCharOffsets.getOrElse(pageIndex) { 0 }

  /**
   * Page containing [charOffset]. Offsets past the end of the chapter clamp to the final page,
   * which is what a resume from a shorter re-pagination wants.
   *
   * An exact match wins over the last-page-at-or-before rule because image- and rule-only pages
   * consume no characters: they share a start offset with the text page after them, and without the
   * exact match such a page could never be resumed onto.
   */
  fun pageIndexOf(charOffset: Int): Int {
    val exact = pageStartCharOffsets.indexOfFirst { it == charOffset }
    if (exact != -1) return exact
    val index = pageStartCharOffsets.indexOfLast { it < charOffset }
    return if (index == -1) 0 else index
  }

  /** Fraction of the chapter read at the start of [pageIndex], in 0..1. */
  fun progressInSpine(pageIndex: Int): Float {
    if (textStreamLength <= 0) return 0f
    return (charOffsetOf(pageIndex).toFloat() / textStreamLength).coerceIn(0f, 1f)
  }

  companion object {
    val Empty = ReaderPagePositions(pageStartCharOffsets = emptyList(), textStreamLength = 0)
  }
}

internal fun ReaderLayoutResult.toPagePositions(): ReaderPagePositions =
  ReaderPagePositions(
    pageStartCharOffsets = pages.map { it.startCharOffset },
    textStreamLength = textStreamLength,
  )
