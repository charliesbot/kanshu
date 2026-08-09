package com.charliesbot.kanshu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.charliesbot.kanshu.core.reader.ReaderHighlightColor

// Highlights and notes are one entity, matching Kavita's AnnotationDto wire shape. A pure
// highlight has `noteBody == null`; a highlight + note has it set. Kavita requires xPath, so a
// "note without a highlight" can't round-trip — we don't model it.
//
// The range is stored as character offsets into the chapter's flattened text stream — the same
// primitive reading progress uses (see ReaderPosition). This table previously held a Readium
// DOM-range locator and planned to derive Kavita's xPath "by walking the rendered DOM"; that
// predates the native engine, which has no live DOM at all. Offsets also survive typography
// changes, where a pixel- or page-anchored highlight would drift off its words the first time
// the reader changed the font. Kavita's xPath is a sync-time projection from the parsed
// document, not something this table caches.
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
  indices = [Index("book_id"), Index(value = ["book_id", "spine_index"])],
)
data class AnnotationEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "book_id") val bookId: String,
  @ColumnInfo(name = "spine_index") val spineIndex: Int,
  @ColumnInfo(name = "start_char_offset") val startCharOffset: Int,
  @ColumnInfo(name = "end_char_offset") val endCharOffset: Int,
  @ColumnInfo(name = "selected_text") val selectedText: String,
  @ColumnInfo(name = "color") val color: String = ReaderHighlightColor.default.key,
  @ColumnInfo(name = "created_at") val createdAt: Long,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
