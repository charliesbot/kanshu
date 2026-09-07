package com.charliesbot.kanshu.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local book registry: [id] owns reading state, while provider IDs identify the remote book.
 * [localPath] records the downloaded EPUB location; Room is authoritative for download status.
 * [providerMetadata] is opaque JSON that acquisition may enrich and catalog refreshes preserve for
 * downloaded books.
 */
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
