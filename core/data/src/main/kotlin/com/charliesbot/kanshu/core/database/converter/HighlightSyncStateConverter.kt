package com.charliesbot.kanshu.core.database.converter

import androidx.room.TypeConverter
import com.charliesbot.kanshu.core.reader.highlight.HighlightSyncState

/** Stores typed highlight sync states as stable SQLite TEXT values. */
class HighlightSyncStateConverter {
  /** Decodes a stored state, rejecting unknown values with [IllegalArgumentException]. */
  @TypeConverter
  fun fromStorageValue(value: String): HighlightSyncState =
    HighlightSyncState.fromStorageValue(value)

  /** Returns the explicit storage value rather than the enum ordinal. */
  @TypeConverter fun toStorageValue(state: HighlightSyncState): String = state.storageValue
}
