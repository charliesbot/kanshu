package com.charliesbot.kanshu.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.charliesbot.kanshu.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {
  @Query("SELECT * FROM reading_progress WHERE book_id = :bookId")
  fun observe(bookId: String): Flow<ReadingProgressEntity?>

  @Query("SELECT * FROM reading_progress WHERE book_id = :bookId")
  suspend fun find(bookId: String): ReadingProgressEntity?

  @Upsert suspend fun upsert(progress: ReadingProgressEntity)

  @Query("DELETE FROM reading_progress WHERE book_id = :bookId") suspend fun delete(bookId: String)
}
