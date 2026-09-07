package com.charliesbot.kanshu.core.kavita.dto

import kotlinx.serialization.Serializable

@Serializable
data class AnnotationDto(
  val id: Int = 0,
  val xPath: String,
  val endingXPath: String = xPath,
  val selectedText: String,
  val selectedSlotIndex: Int,
  val containsSpoiler: Boolean = false,
  val pageNumber: Int,
  val chapterId: Int,
  val volumeId: Int,
  val seriesId: Int,
  val libraryId: Int,
  val createdUtc: String? = null,
  val lastModifiedUtc: String? = null,
)
