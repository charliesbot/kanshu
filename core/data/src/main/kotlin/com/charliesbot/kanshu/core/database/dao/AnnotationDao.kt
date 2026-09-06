package com.charliesbot.kanshu.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.charliesbot.kanshu.core.database.entity.AnnotationEntity
import com.charliesbot.kanshu.core.reader.annotation.HighlightSyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
  @Query(
    "SELECT * FROM annotations WHERE book_id = :bookId AND spine_index = :spineIndex " +
      "AND sync_state != :excludedState ORDER BY start_char_offset ASC"
  )
  fun observeForSpine(
    bookId: String,
    spineIndex: Int,
    excludedState: HighlightSyncState = HighlightSyncState.PENDING_DELETE,
  ): Flow<List<AnnotationEntity>>

  @Query("SELECT * FROM annotations WHERE id = :id") suspend fun find(id: String): AnnotationEntity?

  @Query("SELECT * FROM annotations WHERE book_id = :bookId")
  suspend fun forBook(bookId: String): List<AnnotationEntity>

  @Query(
    "SELECT * FROM annotations WHERE book_id = :bookId AND sync_state = :state " +
      "ORDER BY updated_at ASC"
  )
  suspend fun pending(bookId: String, state: HighlightSyncState): List<AnnotationEntity>

  @Upsert suspend fun upsert(annotation: AnnotationEntity)

  @Upsert suspend fun upsertAll(annotations: List<AnnotationEntity>)

  @Query(
    "UPDATE annotations SET color = :color, updated_at = :updatedAt, sync_state = :syncState " +
      "WHERE id = :id"
  )
  suspend fun updateColor(
    id: String,
    color: String,
    updatedAt: Long,
    syncState: HighlightSyncState,
  )

  @Query("UPDATE annotations SET updated_at = :updatedAt, sync_state = :syncState WHERE id = :id")
  suspend fun markPendingDelete(
    id: String,
    updatedAt: Long,
    syncState: HighlightSyncState = HighlightSyncState.PENDING_DELETE,
  )

  @Query(
    "UPDATE annotations SET remote_id = COALESCE(:remoteId, remote_id), " +
      "sync_state = :syncedState WHERE id = :id AND updated_at = :expectedUpdatedAt " +
      "AND sync_state = :pendingState"
  )
  suspend fun acknowledgeUpsert(
    id: String,
    expectedUpdatedAt: Long,
    remoteId: String?,
    syncedState: HighlightSyncState = HighlightSyncState.SYNCED,
    pendingState: HighlightSyncState = HighlightSyncState.PENDING_UPSERT,
  ): Int

  @Query(
    "DELETE FROM annotations WHERE id = :id AND updated_at = :expectedUpdatedAt AND " +
      "sync_state = :pendingState"
  )
  suspend fun acknowledgeDelete(
    id: String,
    expectedUpdatedAt: Long,
    pendingState: HighlightSyncState = HighlightSyncState.PENDING_DELETE,
  ): Int

  @Query("DELETE FROM annotations WHERE id = :id") suspend fun delete(id: String)

  @Query("DELETE FROM annotations WHERE id IN (:ids)") suspend fun deleteAll(ids: List<String>)
}
