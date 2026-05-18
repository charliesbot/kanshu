package com.charliesbot.kanshu.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.charliesbot.kanshu.core.database.dao.AnnotationDao
import com.charliesbot.kanshu.core.database.dao.BookDao
import com.charliesbot.kanshu.core.database.dao.ReadingProgressDao
import com.charliesbot.kanshu.core.database.entity.AnnotationEntity
import com.charliesbot.kanshu.core.database.entity.BookEntity
import com.charliesbot.kanshu.core.database.entity.ReadingProgressEntity

@Database(
  entities = [BookEntity::class, ReadingProgressEntity::class, AnnotationEntity::class],
  version = 1,
  exportSchema = true,
)
abstract class KanshuDatabase : RoomDatabase() {
  abstract fun bookDao(): BookDao

  abstract fun readingProgressDao(): ReadingProgressDao

  abstract fun annotationDao(): AnnotationDao

  companion object {
    const val NAME = "kanshu.db"
  }
}
