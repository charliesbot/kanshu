package com.charliesbot.kanshu.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.charliesbot.kanshu.core.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
  @Query("SELECT * FROM books") fun observeAll(): Flow<List<BookEntity>>

  @Query("SELECT * FROM books WHERE local_path IS NOT NULL")
  fun observeDownloaded(): Flow<List<BookEntity>>

  @Query("SELECT * FROM books WHERE id = :id") suspend fun find(id: String): BookEntity?

  @Query("SELECT * FROM books WHERE local_path IS NOT NULL")
  suspend fun allDownloaded(): List<BookEntity>

  @Upsert suspend fun upsert(book: BookEntity)

  // Used on startup reconciliation: a missing file flips local_path back to null so the row
  // still represents "we know this book" but no longer claims a download.
  @Query(
    "UPDATE books SET local_path = NULL, byte_size = NULL, downloaded_at = NULL WHERE id = :id"
  )
  suspend fun clearDownload(id: String)

  @Query("DELETE FROM books WHERE id = :id") suspend fun delete(id: String)
}
