package com.charliesbot.kanshu.core.reader.progress

import kotlinx.serialization.Serializable
import org.readium.r2.shared.publication.Publication

/**
 * The reader's persisted place in a book.
 *
 * Stores a character offset rather than a page index. Reflowable pagination shifts whenever font
 * size, margins, or the layout rules change, so a page index is only meaningful under the exact
 * typography that produced it — every preference change silently invalidated it. A character offset
 * into the chapter's flattened text stream survives all of that. See the Progress Model in
 * `docs/PRD_NATIVE_READER.md`.
 */
@Serializable
data class ReaderPosition(
  val spineIndex: Int,
  /**
   * Offset of the page's first character into the chapter's flattened text stream. Defaults to 0 so
   * rows written by the page-index build — which carry no offset, and whose page index cannot be
   * mapped back to one without the typography that wrote it — decode into the chapter start.
   */
  val charOffset: Int = 0,
  /** `charOffset / textStreamLength`, denormalized so sync can read progress without re-layout. */
  val progressInSpine: Float,
)

/**
 * Calculates the overall progression of this [ReaderPosition] relative to the [publication]'s
 * reading order size. Returns a value between 0.0 and 1.0.
 */
fun ReaderPosition.progressionIn(publication: Publication): Double {
  val size = publication.readingOrder.size
  if (size <= 0) return 0.0
  return ((spineIndex.toDouble() + progressInSpine.toDouble()) / size).coerceIn(0.0, 1.0)
}
