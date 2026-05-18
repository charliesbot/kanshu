package com.charliesbot.kanshu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// Unified registry of books this device knows about. The `id` is source-prefixed
// ("kavita:<seriesId>") so reading state stays source-agnostic — when local-folder sources
// arrive, their ids ("local:<uri-hash>") slot in next to Kavita rows without a migration.
//
// `localPath` is the bridge to the filesystem: when non-null, the EPUB lives at that path.
// The FS is no longer authoritative for "what's downloaded" — the DB is. The on-disk file is
// just bytes the DB points at. Uninstall wipes both together (filesDir + Room db are app-private),
// so DB/FS divergence is bounded to in-process bugs we control.
@Entity(tableName = "books")
data class BookEntity(
  @PrimaryKey val id: String,
  val source: String,
  @ColumnInfo(name = "source_item_id") val sourceItemId: String,
  val title: String,
  @ColumnInfo(name = "local_path") val localPath: String?,
  @ColumnInfo(name = "byte_size") val byteSize: Long?,
  @ColumnInfo(name = "downloaded_at") val downloadedAt: Long?,
  @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
)
