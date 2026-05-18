package com.charliesbot.kanshu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Highlights and notes are one entity, matching Kavita's AnnotationDto wire shape. A pure
// highlight has `noteBody == null`; a highlight + note has it set. Kavita requires xPath, so a
// "note without a highlight" can't round-trip — we don't model it.
//
// `locatorJson` is the Readium locator (start/end DOM range serialized). The Kavita-side xPath
// pair is derived at sync time by walking the rendered DOM; we don't pre-cache it here because
// annotations are typically created in-reader where the DOM is live anyway.
@Entity(
  tableName = "annotations",
  foreignKeys =
    [
      ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["book_id"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index("book_id")],
)
data class AnnotationEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "book_id") val bookId: String,
  @ColumnInfo(name = "locator_json") val locatorJson: String,
  @ColumnInfo(name = "selected_text") val selectedText: String,
  val color: Int?,
  @ColumnInfo(name = "note_body") val noteBody: String?,
  @ColumnInfo(name = "contains_spoiler") val containsSpoiler: Boolean,
  @ColumnInfo(name = "created_at") val createdAt: Long,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
