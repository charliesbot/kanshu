package com.charliesbot.kanshu.core.database

import androidx.room.TypeConverter
import com.charliesbot.kanshu.core.reader.annotation.HighlightSyncState

class HighlightSyncStateConverter {
  @TypeConverter
  fun fromStorageValue(value: String): HighlightSyncState =
    HighlightSyncState.fromStorageValue(value)

  @TypeConverter fun toStorageValue(state: HighlightSyncState): String = state.storageValue
}
