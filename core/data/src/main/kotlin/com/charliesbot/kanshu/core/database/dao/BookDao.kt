package com.charliesbot.kanshu.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
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

  @Query("SELECT * FROM books") suspend fun getAll(): List<BookEntity>

  @Upsert suspend fun upsert(book: BookEntity)

  // Used on startup reconciliation: a missing file flips local_path back to null so the row
  // still represents "we know this book" but no longer claims a download.
  @Query(
    "UPDATE books SET local_path = NULL, byte_size = NULL, downloaded_at = NULL WHERE id = :id"
  )
  suspend fun clearDownload(id: String)

  @Query("DELETE FROM books WHERE id = :id") suspend fun delete(id: String)

  @Transaction
  suspend fun syncBooks(source: String, remoteBooks: List<BookEntity>, fetchedIds: Set<String>) {
    val localBooks = getAll()
    val localBooksMap = localBooks.associateBy { it.id }

    val toUpsert = remoteBooks.map { remote ->
      val existing = localBooksMap[remote.id]
      remote.copy(
        localPath = existing?.localPath,
        byteSize = existing?.byteSize,
        downloadedAt = existing?.downloadedAt,
        lastOpenedAt = existing?.lastOpenedAt,
      )
    }
    toUpsert.forEach { upsert(it) }

    val toDelete = localBooks.filter {
      it.source == source && it.id !in fetchedIds && it.localPath == null
    }
    toDelete.forEach { delete(it.id) }
  }
}
