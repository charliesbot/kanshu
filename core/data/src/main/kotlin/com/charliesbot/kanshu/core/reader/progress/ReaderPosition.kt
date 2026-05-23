package com.charliesbot.kanshu.core.reader.progress

import kotlinx.serialization.Serializable

@Serializable
data class ReaderPosition(
  val schemaVersion: Int = 1,
  val spineIndex: Int,
  val pageIndex: Int,
  val progressInSpine: Float,
)
