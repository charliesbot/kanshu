package com.charliesbot.kanshu.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.charliesbot.kanshu.core.database.entity.HighlightEntity
import com.charliesbot.kanshu.core.reader.highlight.HighlightSyncState
import kotlinx.coroutines.flow.Flow

/** Room operations for visible highlights, pending mutations, and guarded acknowledgements. */
@Dao
interface HighlightDao {
  /** Observes highlights in offset order, excluding pending-delete tombstones by default. */
  @Query(
    "SELECT * FROM annotations WHERE book_id = :bookId AND spine_index = :spineIndex " +
      "AND sync_state != :excludedState ORDER BY start_char_offset ASC"
  )
  fun observeForSpine(
    bookId: String,
    spineIndex: Int,
    excludedState: HighlightSyncState = HighlightSyncState.PENDING_DELETE,
  ): Flow<List<HighlightEntity>>

  /** Returns a row by local ID, including tombstones, or null if absent. */
  @Query("SELECT * FROM annotations WHERE id = :id") suspend fun find(id: String): HighlightEntity?

  /** Returns all book highlights, including tombstones, for reconciliation. */
  @Query("SELECT * FROM annotations WHERE book_id = :bookId")
  suspend fun forBook(bookId: String): List<HighlightEntity>

  /** Returns rows in a requested state ordered by their last local update. */
  @Query(
    "SELECT * FROM annotations WHERE book_id = :bookId AND sync_state = :state " +
      "ORDER BY updated_at ASC"
  )
  suspend fun pending(bookId: String, state: HighlightSyncState): List<HighlightEntity>

  @Upsert suspend fun upsert(highlight: HighlightEntity)

  /** Inserts or updates a batch; callers supply the transaction when reconciling a snapshot. */
  @Upsert suspend fun upsertAll(highlights: List<HighlightEntity>)

  /** Updates color, timestamp, and sync status together in one statement. */
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

  /** Marks a highlight as a hidden pending-delete tombstone by default. */
  @Query("UPDATE annotations SET updated_at = :updatedAt, sync_state = :syncState WHERE id = :id")
  suspend fun markPendingDelete(
    id: String,
    updatedAt: Long,
    syncState: HighlightSyncState = HighlightSyncState.PENDING_DELETE,
  )

  /** Acknowledges only the expected pending version; returns the number of updated rows. */
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

  /** Deletes only the expected pending tombstone; returns the number of removed rows. */
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

  /** Physically removes a batch of local IDs during snapshot reconciliation. */
  @Query("DELETE FROM annotations WHERE id IN (:ids)") suspend fun deleteAll(ids: List<String>)
}
