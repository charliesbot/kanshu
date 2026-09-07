package com.charliesbot.kanshu.core.database.converter

import androidx.room.TypeConverter
import com.charliesbot.kanshu.core.reader.SourceElementPath
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SourceElementPathConverter {
  @TypeConverter
  fun fromStorageValue(value: String): SourceElementPath =
    SourceElementPath(Json.decodeFromString(value))

  @TypeConverter
  fun toStorageValue(path: SourceElementPath): String = Json.encodeToString(path.childIndexes)
}
