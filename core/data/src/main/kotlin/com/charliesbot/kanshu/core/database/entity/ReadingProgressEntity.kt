package com.charliesbot.kanshu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// One row per book. `locatorJson` now persists `ReaderPosition` JSON (rather than Readium locator
// JSON)
// — local canonical position, always restorable on the same device. `progression` is denormalized
// from the position (0..1 book-level) for cheap "% read" queries without parsing the JSON.
@Entity(
  tableName = "reading_progress",
  foreignKeys =
    [
      ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["book_id"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
)
data class ReadingProgressEntity(
  @PrimaryKey @ColumnInfo(name = "book_id") val bookId: String,
  @ColumnInfo(name = "locator_json") val locatorJson: String,
  val progression: Double,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
