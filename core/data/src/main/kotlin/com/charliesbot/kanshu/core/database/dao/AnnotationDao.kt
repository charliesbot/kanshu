package com.charliesbot.kanshu.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.charliesbot.kanshu.core.database.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
  @Query(
    "SELECT * FROM annotations WHERE book_id = :bookId AND spine_index = :spineIndex " +
      "AND sync_state != 'PENDING_DELETE' ORDER BY start_char_offset ASC"
  )
  fun observeForSpine(bookId: String, spineIndex: Int): Flow<List<AnnotationEntity>>

  @Query("SELECT * FROM annotations WHERE id = :id") suspend fun find(id: String): AnnotationEntity?

  @Query("SELECT * FROM annotations WHERE book_id = :bookId")
  suspend fun forBook(bookId: String): List<AnnotationEntity>

  @Query(
    "SELECT * FROM annotations WHERE book_id = :bookId AND sync_state = :state " +
      "ORDER BY updated_at ASC"
  )
  suspend fun pending(bookId: String, state: String): List<AnnotationEntity>

  @Upsert suspend fun upsert(annotation: AnnotationEntity)

  @Query(
    "UPDATE annotations SET color = :color, updated_at = :updatedAt, sync_state = :syncState " +
      "WHERE id = :id"
  )
  suspend fun updateColor(id: String, color: String, updatedAt: Long, syncState: String)

  @Query(
    "UPDATE annotations SET updated_at = :updatedAt, sync_state = 'PENDING_DELETE' WHERE id = :id"
  )
  suspend fun markPendingDelete(id: String, updatedAt: Long)

  @Query(
    "UPDATE annotations SET remote_id = COALESCE(:remoteId, remote_id), sync_state = 'SYNCED' " +
      "WHERE id = :id AND updated_at = :expectedUpdatedAt AND sync_state = 'PENDING_UPSERT'"
  )
  suspend fun acknowledgeUpsert(id: String, expectedUpdatedAt: Long, remoteId: String?): Int

  @Query(
    "DELETE FROM annotations WHERE id = :id AND updated_at = :expectedUpdatedAt " +
      "AND sync_state = 'PENDING_DELETE'"
  )
  suspend fun acknowledgeDelete(id: String, expectedUpdatedAt: Long): Int

  @Query("DELETE FROM annotations WHERE id = :id") suspend fun delete(id: String)
}
