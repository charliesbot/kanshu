package com.charliesbot.kanshu.navigator

import android.graphics.RectF
import com.charliesbot.kanshu.core.reader.ReaderHighlightColor

/**
 * A stored highlight, addressed the same way reading progress is: a half-open range of character
 * offsets into the chapter's flattened text stream.
 *
 * Deliberately not page- or rect-based. Pagination shifts with every typography change, so a
 * highlight pinned to pixels or page indexes would drift off its words the first time the reader
 * changed the font. See the Progress Model in `docs/design/native-reader.md`.
 */
data class ReaderHighlight(
  val startCharOffset: Int,
  val endCharOffset: Int,
  val id: String = "",
  val color: ReaderHighlightColor = ReaderHighlightColor.default,
) {
  init {
    require(endCharOffset > startCharOffset) {
      "Highlight must cover at least one character: $startCharOffset..$endCharOffset"
    }
  }

  /** The offsets as a half-open range. The single place that conversion happens. */
  internal val range: IntRange
    get() = startCharOffset until endCharOffset
}

/** A stored highlight hit by a tap, anchored to its visible geometry on the current page. */
data class ReaderHighlightTap(val highlight: ReaderHighlight, val anchor: RectF)

internal data class RenderedHighlight(val highlight: ReaderHighlight, val rect: RectF)

/**
 * What the reader currently has selected. Carries the text for display and the stream range so the
 * consumer can turn the selection into a [ReaderHighlight] without re-deriving geometry.
 *
 * The range spans the whole selection, including the parts scrolled onto other pages when a drag
 * carried across a page boundary.
 */
data class ReaderSelectionInfo(
  val text: String,
  val anchor: RectF,
  val startCharOffset: Int,
  val endCharOffset: Int,
) {
  /** False when the engine reported a selection it could not resolve to stream offsets. */
  val hasRange: Boolean
    get() = endCharOffset > startCharOffset
}
