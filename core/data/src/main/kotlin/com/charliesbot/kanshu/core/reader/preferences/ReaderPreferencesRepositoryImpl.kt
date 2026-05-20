package com.charliesbot.kanshu.core.reader.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.reader.ReaderPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReaderPreferencesRepositoryImpl(private val dataStore: DataStore<Preferences>) :
  ReaderPreferencesRepository {

  override val preferences: Flow<ReaderPreferences> =
    dataStore.data.map { prefs ->
      val storedFont = prefs[FONT_KEY]?.let(::fontFromStorage) ?: ReaderPreferences().font
      val storedScale =
        prefs[SCALE_KEY]?.coerceIn(ReaderPreferences.SCALE_MIN, ReaderPreferences.SCALE_MAX)
          ?: ReaderPreferences().fontScale
      ReaderPreferences(font = storedFont, fontScale = storedScale)
    }

  override suspend fun setFont(font: ReaderFont) {
    dataStore.edit { it[FONT_KEY] = font.name }
  }

  override suspend fun setFontScale(scale: Float) {
    val clamped = scale.coerceIn(ReaderPreferences.SCALE_MIN, ReaderPreferences.SCALE_MAX)
    dataStore.edit { it[SCALE_KEY] = clamped }
  }

  // Unknown stored names (e.g. an enum entry removed in a later version) fall back to the default
  // rather than throwing on Flow collection.
  private fun fontFromStorage(name: String): ReaderFont =
    ReaderFont.entries.firstOrNull { it.name == name } ?: ReaderPreferences().font

  private companion object {
    val FONT_KEY = stringPreferencesKey("font")
    val SCALE_KEY = floatPreferencesKey("font_scale")
  }
}
