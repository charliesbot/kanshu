package com.charliesbot.kanshu.core.reader.annotation

import com.charliesbot.kanshu.core.database.dao.AnnotationDao
import com.charliesbot.kanshu.core.database.entity.AnnotationEntity
import com.charliesbot.kanshu.core.reader.ReaderHighlightColor
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A highlight the reader made, addressed by character offsets into the chapter's flattened text
 * stream — the same primitive reading progress uses, so highlights stay on their words when
 * typography changes repaginate the chapter.
 */
data class ReaderAnnotation(
  val id: String,
  val spineIndex: Int,
  val startCharOffset: Int,
  val endCharOffset: Int,
  val selectedText: String,
  val color: ReaderHighlightColor = ReaderHighlightColor.default,
  val createdAt: Long = 0L,
)

interface AnnotationRepository {
  /** Highlights for one chapter, ordered by position, updating as they are added or removed. */
  fun observeForSpine(bookId: String, spineIndex: Int): Flow<List<ReaderAnnotation>>

  /** Stores a highlight and returns it. Returns null for an empty range. */
  suspend fun addHighlight(
    bookId: String,
    spineIndex: Int,
    startCharOffset: Int,
    endCharOffset: Int,
    selectedText: String,
    color: ReaderHighlightColor = ReaderHighlightColor.default,
  ): ReaderAnnotation?

  suspend fun updateHighlightColor(id: String, color: ReaderHighlightColor)

  /**
   * Removes a highlight. Deleting a book cascades to its highlights, so this is for the reader's
   * own delete action.
   */
  suspend fun delete(id: String)
}

class AnnotationRepositoryImpl(
  private val annotationDao: AnnotationDao,
  private val now: () -> Long = System::currentTimeMillis,
  private val newId: () -> String = { UUID.randomUUID().toString() },
) : AnnotationRepository {

  override fun observeForSpine(bookId: String, spineIndex: Int): Flow<List<ReaderAnnotation>> =
    annotationDao.observeForSpine(bookId, spineIndex).map { rows -> rows.map { it.toAnnotation() } }

  override suspend fun addHighlight(
    bookId: String,
    spineIndex: Int,
    startCharOffset: Int,
    endCharOffset: Int,
    selectedText: String,
    color: ReaderHighlightColor,
  ): ReaderAnnotation? {
    if (endCharOffset <= startCharOffset) return null
    val timestamp = now()
    val annotation =
      ReaderAnnotation(
        id = newId(),
        spineIndex = spineIndex,
        startCharOffset = startCharOffset,
        endCharOffset = endCharOffset,
        selectedText = selectedText,
        color = color,
        createdAt = timestamp,
      )
    annotationDao.upsert(annotation.toEntity(bookId = bookId, updatedAt = timestamp))
    return annotation
  }

  override suspend fun updateHighlightColor(id: String, color: ReaderHighlightColor) =
    annotationDao.updateColor(id = id, color = color.key, updatedAt = now())

  override suspend fun delete(id: String) = annotationDao.delete(id)
}

private fun AnnotationEntity.toAnnotation(): ReaderAnnotation =
  ReaderAnnotation(
    id = id,
    spineIndex = spineIndex,
    startCharOffset = startCharOffset,
    endCharOffset = endCharOffset,
    selectedText = selectedText,
    color = ReaderHighlightColor.fromStorageValue(color),
    createdAt = createdAt,
  )

private fun ReaderAnnotation.toEntity(bookId: String, updatedAt: Long): AnnotationEntity =
  AnnotationEntity(
    id = id,
    bookId = bookId,
    spineIndex = spineIndex,
    startCharOffset = startCharOffset,
    endCharOffset = endCharOffset,
    selectedText = selectedText,
    color = color.key,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )
