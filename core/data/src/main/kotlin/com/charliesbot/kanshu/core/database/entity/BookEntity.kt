package com.charliesbot.kanshu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Unified registry of books this device knows about. The stable Kanshu `id` owns reading state;
// providerInstanceId + providerItemId identify the book at its origin without parsing `id`.
//
// `localPath` is the bridge to the filesystem: when non-null, the EPUB lives at that path.
// The FS is no longer authoritative for "what's downloaded" — the DB is. The on-disk file is
// just bytes the DB points at. Uninstall wipes both together (filesDir + Room db are app-private),
// so DB/FS divergence is bounded to in-process bugs we control.
@Entity(
  tableName = "books",
  indices =
    [
      Index(
        value = ["provider_instance_id", "provider_item_id"],
        unique = true,
      )
    ],
)
data class BookEntity(
  @PrimaryKey val id: String,
  @ColumnInfo(name = "provider_instance_id") val providerInstanceId: String,
  @ColumnInfo(name = "provider_item_id") val providerItemId: String,
  val title: String,
  @ColumnInfo(name = "local_path") val localPath: String?,
  @ColumnInfo(name = "byte_size") val byteSize: Long?,
  @ColumnInfo(name = "downloaded_at") val downloadedAt: Long?,
  @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
  @ColumnInfo(name = "cover_token") val coverToken: String? = null,
  @ColumnInfo(name = "provider_metadata") val providerMetadata: String? = null,
)
