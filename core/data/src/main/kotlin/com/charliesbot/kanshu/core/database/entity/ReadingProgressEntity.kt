package com.charliesbot.kanshu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// One row per book. `locatorJson` now persists `ReaderPosition` JSON (rather than Readium locator
// JSON)
// — local canonical position, always restorable on the same device. `progression` is denormalized
// from the position (0..1 book-level) for cheap "% read" queries without parsing the JSON.
//
// `syncMetadata` is an opaque JSON blob written by whichever provider owns the book (see
// books.source). The reader doesn't read or interpret it; the sync layer parses it based on
// the source discriminator. This is how we keep the schema provider-agnostic — Kavita writes
// {chapterId, seriesId, volumeId, libraryId, pageNum, bookScrollId}; another provider writes
// its own shape; the table doesn't care. Adding a new provider doesn't require a migration.
//
// Why cache the provider-side payload at all instead of recomputing at sync time: at save time
// the reader has the rendered DOM and the open Readium Publication in hand. Deriving the
// projection (e.g., nearest id'd element → bookScrollId) is cheap there. At sync time the
// publication is typically closed and reopening would be expensive.
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
  @ColumnInfo(name = "sync_metadata") val syncMetadata: String?,
)
