package com.charliesbot.kanshu.core.reader.progress

import kotlinx.serialization.Serializable
import org.readium.r2.shared.publication.Publication

@Serializable
data class ReaderPosition(
  val schemaVersion: Int = 1,
  val spineIndex: Int,
  val pageIndex: Int,
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
