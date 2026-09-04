package com.charliesbot.kanshu.core.reader

import kotlinx.serialization.Serializable

/** Zero-based element-child indexes relative to the XHTML body. */
@Serializable
data class SourceElementPath(val childIndexes: List<Int>) {
  init {
    require(childIndexes.all { it >= 0 }) { "Source element indexes must be non-negative" }
  }

  fun child(index: Int): SourceElementPath = SourceElementPath(childIndexes + index)

  companion object {
    val Root = SourceElementPath(emptyList())
  }
}
