package com.charliesbot.kanshu.core.reader.annotation

import com.charliesbot.kanshu.core.database.dao.AnnotationDao
import com.charliesbot.kanshu.core.database.entity.AnnotationEntity
import com.charliesbot.kanshu.core.provider.HighlightChange
import com.charliesbot.kanshu.core.provider.ProviderHighlight
import com.charliesbot.kanshu.core.provider.ProviderHighlightSnapshot
import com.charliesbot.kanshu.core.reader.ReaderHighlightColor
import com.charliesbot.kanshu.core.reader.SourceElementPath
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class HighlightSyncState {
  SYNCED,
  PENDING_UPSERT,
  PENDING_DELETE,
}

data class ReaderAnnotation(
  val id: String,
  val bookId: String = "",
  val spineIndex: Int,
  val startCharOffset: Int,
  val endCharOffset: Int,
  val selectedText: String,
  val startElementPath: SourceElementPath = SourceElementPath.Root,
  val endElementPath: SourceElementPath = SourceElementPath.Root,
  val color: ReaderHighlightColor = ReaderHighlightColor.default,
  val createdAt: Long = 0L,
  val updatedAt: Long = createdAt,
  val remoteId: String? = null,
  val syncState: HighlightSyncState = HighlightSyncState.SYNCED,
)

interface AnnotationRepository {
  fun observeForSpine(bookId: String, spineIndex: Int): Flow<List<ReaderAnnotation>>

  suspend fun addHighlight(
    bookId: String,
    spineIndex: Int,
    startCharOffset: Int,
    endCharOffset: Int,
    selectedText: String,
    color: ReaderHighlightColor = ReaderHighlightColor.default,
  ): ReaderAnnotation?

  suspend fun addHighlight(
    bookId: String,
    spineIndex: Int,
    startCharOffset: Int,
    endCharOffset: Int,
    selectedText: String,
    startElementPath: SourceElementPath,
    endElementPath: SourceElementPath,
    color: ReaderHighlightColor = ReaderHighlightColor.default,
  ): ReaderAnnotation? =
    addHighlight(bookId, spineIndex, startCharOffset, endCharOffset, selectedText, color)

  suspend fun updateHighlightColor(id: String, color: ReaderHighlightColor)

  suspend fun delete(id: String)

  suspend fun pendingChanges(
    bookId: String,
    state: HighlightSyncState,
  ): List<HighlightChange> = emptyList()

  suspend fun acknowledgeUpsert(id: String, expectedUpdatedAt: Long, remoteId: String?) = Unit

  suspend fun acknowledgeDelete(id: String, expectedUpdatedAt: Long) = Unit

  suspend fun applySnapshot(bookId: String, snapshot: ProviderHighlightSnapshot) = Unit
}

class AnnotationRepositoryImpl(
  private val annotationDao: AnnotationDao,
  private val inTransaction: suspend (suspend () -> Unit) -> Unit,
  private val highlightSyncEnabled: suspend (String) -> Boolean = { false },
  private val now: () -> Long = System::currentTimeMillis,
  private val newId: () -> String = { UUID.randomUUID().toString() },
  private val json: Json = Json,
) : AnnotationRepository {
  override fun observeForSpine(bookId: String, spineIndex: Int): Flow<List<ReaderAnnotation>> =
    annotationDao.observeForSpine(bookId, spineIndex).map { rows ->
      rows.map { it.toAnnotation(json) }
    }

  override suspend fun addHighlight(
    bookId: String,
    spineIndex: Int,
    startCharOffset: Int,
    endCharOffset: Int,
    selectedText: String,
    color: ReaderHighlightColor,
  ): ReaderAnnotation? =
    addHighlight(
      bookId,
      spineIndex,
      startCharOffset,
      endCharOffset,
      selectedText,
      SourceElementPath.Root,
      SourceElementPath.Root,
      color,
    )

  override suspend fun addHighlight(
    bookId: String,
    spineIndex: Int,
    startCharOffset: Int,
    endCharOffset: Int,
    selectedText: String,
    startElementPath: SourceElementPath,
    endElementPath: SourceElementPath,
    color: ReaderHighlightColor,
  ): ReaderAnnotation? {
    if (endCharOffset <= startCharOffset) return null
    val timestamp = now()
    val state =
      if (highlightSyncEnabled(bookId)) HighlightSyncState.PENDING_UPSERT
      else HighlightSyncState.SYNCED
    val annotation =
      ReaderAnnotation(
        id = newId(),
        bookId = bookId,
        spineIndex = spineIndex,
        startCharOffset = startCharOffset,
        endCharOffset = endCharOffset,
        selectedText = selectedText,
        startElementPath = startElementPath,
        endElementPath = endElementPath,
        color = color,
        createdAt = timestamp,
        updatedAt = timestamp,
        syncState = state,
      )
    annotationDao.upsert(annotation.toEntity(json))
    return annotation
  }

  override suspend fun updateHighlightColor(id: String, color: ReaderHighlightColor) {
    val annotation = annotationDao.find(id) ?: return
    val state =
      if (highlightSyncEnabled(annotation.bookId)) HighlightSyncState.PENDING_UPSERT
      else HighlightSyncState.SYNCED
    annotationDao.updateColor(id, color.key, now(), state.name)
  }

  override suspend fun delete(id: String) {
    val annotation = annotationDao.find(id) ?: return
    if (highlightSyncEnabled(annotation.bookId) && annotation.remoteId != null) {
      annotationDao.markPendingDelete(id, now())
    } else {
      annotationDao.delete(id)
    }
  }

  override suspend fun pendingChanges(
    bookId: String,
    state: HighlightSyncState,
  ): List<HighlightChange> =
    annotationDao.pending(bookId, state.name).map { row ->
      when (state) {
        HighlightSyncState.PENDING_DELETE ->
          HighlightChange.Delete(row.id, row.remoteId, row.updatedAt)
        HighlightSyncState.PENDING_UPSERT -> row.toUpsert(json)
        HighlightSyncState.SYNCED -> error("SYNCED rows are not pending changes")
      }
    }

  override suspend fun acknowledgeUpsert(
    id: String,
    expectedUpdatedAt: Long,
    remoteId: String?,
  ) {
    annotationDao.acknowledgeUpsert(id, expectedUpdatedAt, remoteId)
  }

  override suspend fun acknowledgeDelete(id: String, expectedUpdatedAt: Long) {
    annotationDao.acknowledgeDelete(id, expectedUpdatedAt)
  }

  override suspend fun applySnapshot(bookId: String, snapshot: ProviderHighlightSnapshot) {
    inTransaction {
      val existing = annotationDao.forBook(bookId)
      val byRemoteId = existing.mapNotNull { row -> row.remoteId?.let { it to row } }.toMap()
      val upserts =
        snapshot.highlights.mapNotNull { remote ->
          val local = byRemoteId[remote.remoteId]
          val localId =
            when {
              local == null -> newId()
              local.syncState == HighlightSyncState.SYNCED.name -> local.id
              else -> return@mapNotNull null
            }
          remote.toAnnotation(bookId, localId).toEntity(json)
        }
      val deletions =
        existing
          .filter {
            it.remoteId != null &&
              it.syncState == HighlightSyncState.SYNCED.name &&
              it.remoteId !in snapshot.seenRemoteIds
          }
          .map { it.id }

      if (upserts.isNotEmpty()) annotationDao.upsertAll(upserts)
      if (deletions.isNotEmpty()) annotationDao.deleteAll(deletions)
    }
  }
}

private fun ProviderHighlight.toAnnotation(bookId: String, id: String): ReaderAnnotation =
  ReaderAnnotation(
    id = id,
    bookId = bookId,
    spineIndex = spineIndex,
    startCharOffset = startCharOffset,
    endCharOffset = endCharOffset,
    selectedText = selectedText,
    startElementPath = startElementPath,
    endElementPath = endElementPath,
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt,
    remoteId = remoteId,
    syncState = HighlightSyncState.SYNCED,
  )

private fun AnnotationEntity.toAnnotation(json: Json): ReaderAnnotation =
  ReaderAnnotation(
    id = id,
    bookId = bookId,
    spineIndex = spineIndex,
    startCharOffset = startCharOffset,
    endCharOffset = endCharOffset,
    selectedText = selectedText,
    startElementPath = SourceElementPath(json.decodeFromString(startElementPath)),
    endElementPath = SourceElementPath(json.decodeFromString(endElementPath)),
    color = ReaderHighlightColor.fromStorageValue(color),
    createdAt = createdAt,
    updatedAt = updatedAt,
    remoteId = remoteId,
    syncState =
      runCatching { HighlightSyncState.valueOf(syncState) }.getOrDefault(HighlightSyncState.SYNCED),
  )

private fun ReaderAnnotation.toEntity(json: Json): AnnotationEntity =
  AnnotationEntity(
    id = id,
    bookId = bookId,
    spineIndex = spineIndex,
    startCharOffset = startCharOffset,
    endCharOffset = endCharOffset,
    selectedText = selectedText,
    startElementPath = json.encodeToString(startElementPath.childIndexes),
    endElementPath = json.encodeToString(endElementPath.childIndexes),
    color = color.key,
    createdAt = createdAt,
    updatedAt = updatedAt,
    remoteId = remoteId,
    syncState = syncState.name,
  )

private fun AnnotationEntity.toUpsert(json: Json): HighlightChange.Upsert =
  HighlightChange.Upsert(
    localId = id,
    remoteId = remoteId,
    expectedUpdatedAt = updatedAt,
    spineIndex = spineIndex,
    startCharOffset = startCharOffset,
    endCharOffset = endCharOffset,
    selectedText = selectedText,
    startElementPath = SourceElementPath(json.decodeFromString(startElementPath)),
    endElementPath = SourceElementPath(json.decodeFromString(endElementPath)),
    color = ReaderHighlightColor.fromStorageValue(color),
    createdAt = createdAt,
  )
