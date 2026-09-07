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

/**
 * Durable local sync status with explicit storage values independent of enum ordinals. SYNCED also
 * represents local-only highlights; PENDING_DELETE rows are hidden tombstones.
 */
enum class HighlightSyncState(val storageValue: String) {
  SYNCED("SYNCED"),
  PENDING_UPSERT("PENDING_UPSERT"),
  PENDING_DELETE("PENDING_DELETE");

  companion object {
    /** Decodes a persisted state, throwing [IllegalArgumentException] for unknown values. */
    fun fromStorageValue(value: String): HighlightSyncState =
      entries.firstOrNull { it.storageValue == value }
        ?: throw IllegalArgumentException("Unknown highlight sync state: $value")
  }
}

/**
 * A local highlight with rendering offsets, source-element anchors, and durable sync bookkeeping.
 * Offsets are relative to one spine section with an exclusive end; timestamps are epoch
 * milliseconds. A null [remoteId] means the highlight has not been linked to a remote annotation.
 */
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

/** Local-first highlight storage and guarded reconciliation with provider changes. */
interface AnnotationRepository {
  /**
   * Observes visible highlights in offset order for one spine section, excluding delete tombstones.
   */
  fun observeForSpine(bookId: String, spineIndex: Int): Flow<List<ReaderAnnotation>>

  /**
   * Persists a highlight before any network work; returns null for an empty or inverted range.
   * Sync-capable books start pending upsert; local-only books start synced.
   */
  suspend fun addHighlight(
    bookId: String,
    spineIndex: Int,
    startCharOffset: Int,
    endCharOffset: Int,
    selectedText: String,
    startElementPath: SourceElementPath,
    endElementPath: SourceElementPath,
    color: ReaderHighlightColor = ReaderHighlightColor.default,
  ): ReaderAnnotation?

  /** Recolors locally and marks sync-capable highlights pending upsert; absent IDs are ignored. */
  suspend fun updateHighlightColor(id: String, color: ReaderHighlightColor)

  /**
   * Hides remotely linked, sync-capable highlights as pending-delete tombstones. Unlinked or
   * local-only highlights are physically removed; absent IDs are ignored.
   */
  suspend fun delete(id: String)

  /** Returns mutations in timestamp order for a pending [state]; SYNCED is not a mutation state. */
  suspend fun pendingChanges(
    bookId: String,
    state: HighlightSyncState,
  ): List<HighlightChange>

  /**
   * Marks a pending upsert synced only if [expectedUpdatedAt] still matches. A null [remoteId]
   * preserves the existing remote link; newer local changes remain pending.
   */
  suspend fun acknowledgeUpsert(id: String, expectedUpdatedAt: Long, remoteId: String?)

  /** Physically removes a pending-delete row only if [expectedUpdatedAt] still matches. */
  suspend fun acknowledgeDelete(id: String, expectedUpdatedAt: Long)

  /**
   * Atomically imports remote highlights, updates linked synced rows, and removes missing synced
   * rows. Pending local changes win; IDs reported as seen but untranslated are preserved.
   */
  suspend fun applySnapshot(bookId: String, snapshot: ProviderHighlightSnapshot)
}

/**
 * Room-backed highlight storage with injected capability lookup and transaction execution. Local
 * mutations do not perform network requests; the sync coordinator submits pending work.
 */
class AnnotationRepositoryImpl(
  private val annotationDao: AnnotationDao,
  private val inTransaction: suspend (suspend () -> Unit) -> Unit,
  private val highlightSyncEnabled: suspend (String) -> Boolean = { false },
  private val now: () -> Long = System::currentTimeMillis,
  private val newId: () -> String = { UUID.randomUUID().toString() },
) : AnnotationRepository {
  override fun observeForSpine(bookId: String, spineIndex: Int): Flow<List<ReaderAnnotation>> =
    annotationDao.observeForSpine(bookId, spineIndex).map { rows ->
      rows.map(AnnotationEntity::toAnnotation)
    }

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
    val state = syncStateFor(bookId)
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
    annotationDao.upsert(annotation.toEntity())
    return annotation
  }

  override suspend fun updateHighlightColor(id: String, color: ReaderHighlightColor) {
    val annotation = annotationDao.find(id) ?: return
    annotationDao.updateColor(id, color.key, now(), syncStateFor(annotation.bookId))
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
    annotationDao.pending(bookId, state).map { row ->
      when (state) {
        HighlightSyncState.PENDING_DELETE ->
          HighlightChange.Delete(row.id, row.remoteId, row.updatedAt)
        HighlightSyncState.PENDING_UPSERT -> row.toUpsert()
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
              local.syncState == HighlightSyncState.SYNCED -> local.id
              else -> return@mapNotNull null
            }
          remote.toAnnotation(bookId, localId).toEntity()
        }
      val deletions =
        existing
          .filter {
            it.remoteId != null &&
              it.syncState == HighlightSyncState.SYNCED &&
              it.remoteId !in snapshot.seenRemoteIds
          }
          .map { it.id }

      if (upserts.isNotEmpty()) annotationDao.upsertAll(upserts)
      if (deletions.isNotEmpty()) annotationDao.deleteAll(deletions)
    }
  }

  private suspend fun syncStateFor(bookId: String): HighlightSyncState =
    if (highlightSyncEnabled(bookId)) HighlightSyncState.PENDING_UPSERT
    else HighlightSyncState.SYNCED
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

private fun AnnotationEntity.toAnnotation(): ReaderAnnotation =
  ReaderAnnotation(
    id = id,
    bookId = bookId,
    spineIndex = spineIndex,
    startCharOffset = startCharOffset,
    endCharOffset = endCharOffset,
    selectedText = selectedText,
    startElementPath = startElementPath,
    endElementPath = endElementPath,
    color = ReaderHighlightColor.fromStorageValue(color),
    createdAt = createdAt,
    updatedAt = updatedAt,
    remoteId = remoteId,
    syncState = syncState,
  )

private fun ReaderAnnotation.toEntity(): AnnotationEntity =
  AnnotationEntity(
    id = id,
    bookId = bookId,
    spineIndex = spineIndex,
    startCharOffset = startCharOffset,
    endCharOffset = endCharOffset,
    selectedText = selectedText,
    startElementPath = startElementPath,
    endElementPath = endElementPath,
    color = color.key,
    createdAt = createdAt,
    updatedAt = updatedAt,
    remoteId = remoteId,
    syncState = syncState,
  )

private fun AnnotationEntity.toUpsert(): HighlightChange.Upsert =
  HighlightChange.Upsert(
    localId = id,
    remoteId = remoteId,
    expectedUpdatedAt = updatedAt,
    spineIndex = spineIndex,
    startCharOffset = startCharOffset,
    endCharOffset = endCharOffset,
    selectedText = selectedText,
    startElementPath = startElementPath,
    endElementPath = endElementPath,
    color = ReaderHighlightColor.fromStorageValue(color),
    createdAt = createdAt,
  )
