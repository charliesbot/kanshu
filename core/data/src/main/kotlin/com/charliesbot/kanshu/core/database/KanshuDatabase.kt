package com.charliesbot.kanshu.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.charliesbot.kanshu.core.database.converter.HighlightSyncStateConverter
import com.charliesbot.kanshu.core.database.converter.SourceElementPathConverter
import com.charliesbot.kanshu.core.database.dao.BookDao
import com.charliesbot.kanshu.core.database.dao.HighlightDao
import com.charliesbot.kanshu.core.database.dao.ReadingProgressDao
import com.charliesbot.kanshu.core.database.entity.BookEntity
import com.charliesbot.kanshu.core.database.entity.HighlightEntity
import com.charliesbot.kanshu.core.database.entity.ReadingProgressEntity

/** Room database for books, reading progress, and durable highlight sync state. */
@Database(
  entities = [BookEntity::class, ReadingProgressEntity::class, HighlightEntity::class],
  version = 8,
  exportSchema = false,
)
@TypeConverters(HighlightSyncStateConverter::class, SourceElementPathConverter::class)
abstract class KanshuDatabase : RoomDatabase() {
  abstract fun bookDao(): BookDao

  abstract fun readingProgressDao(): ReadingProgressDao

  abstract fun highlightDao(): HighlightDao

  companion object {
    const val NAME = "kanshu.db"
  }
}
