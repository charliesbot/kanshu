package com.charliesbot.kanshu.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.charliesbot.kanshu.core.database.entity.AnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
  /**
   * The reader only ever renders the chapter it is showing, so it observes one spine item rather
   * than filtering the whole book on every page turn.
   */
  @Query(
    "SELECT * FROM annotations WHERE book_id = :bookId AND spine_index = :spineIndex " +
      "ORDER BY start_char_offset ASC"
  )
  fun observeForSpine(bookId: String, spineIndex: Int): Flow<List<AnnotationEntity>>

  @Upsert suspend fun upsert(annotation: AnnotationEntity)

  @Query("DELETE FROM annotations WHERE id = :id") suspend fun delete(id: String)
}
