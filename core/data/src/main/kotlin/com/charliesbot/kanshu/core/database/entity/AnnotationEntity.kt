package com.charliesbot.kanshu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.charliesbot.kanshu.core.reader.ReaderHighlightColor

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
  indices =
    [
      Index("book_id"),
      Index(value = ["book_id", "spine_index"]),
      Index(value = ["book_id", "remote_id"], unique = true),
    ],
)
data class AnnotationEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "book_id") val bookId: String,
  @ColumnInfo(name = "spine_index") val spineIndex: Int,
  @ColumnInfo(name = "start_char_offset") val startCharOffset: Int,
  @ColumnInfo(name = "end_char_offset") val endCharOffset: Int,
  @ColumnInfo(name = "selected_text") val selectedText: String,
  @ColumnInfo(name = "start_element_path") val startElementPath: String = "[]",
  @ColumnInfo(name = "end_element_path") val endElementPath: String = "[]",
  @ColumnInfo(name = "color") val color: String = ReaderHighlightColor.default.key,
  @ColumnInfo(name = "created_at") val createdAt: Long,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
  @ColumnInfo(name = "remote_id") val remoteId: String? = null,
  @ColumnInfo(name = "sync_state") val syncState: String = "SYNCED",
)
