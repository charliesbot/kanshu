package com.charliesbot.kanshu.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.charliesbot.kanshu.core.database.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
  @Query("SELECT * FROM annotations WHERE book_id = :bookId ORDER BY created_at ASC")
  fun observeForBook(bookId: String): Flow<List<AnnotationEntity>>

  @Query("SELECT * FROM annotations WHERE id = :id") suspend fun find(id: String): AnnotationEntity?

  @Upsert suspend fun upsert(annotation: AnnotationEntity)

  @Query("DELETE FROM annotations WHERE id = :id") suspend fun delete(id: String)
}
