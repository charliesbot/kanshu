package com.charliesbot.kanshu.core.database.converter

import androidx.room.TypeConverter
import com.charliesbot.kanshu.core.reader.SourceElementPath
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stores body-relative element paths as JSON integer arrays in SQLite TEXT columns. */
class SourceElementPathConverter {
  /** Decodes a stored JSON child-index array; malformed data is not silently replaced with root. */
  @TypeConverter
  fun fromStorageValue(value: String): SourceElementPath =
    SourceElementPath(Json.decodeFromString(value))

  /** Encodes the child indexes, preserving the existing JSON array storage format. */
  @TypeConverter
  fun toStorageValue(path: SourceElementPath): String = Json.encodeToString(path.childIndexes)
}
