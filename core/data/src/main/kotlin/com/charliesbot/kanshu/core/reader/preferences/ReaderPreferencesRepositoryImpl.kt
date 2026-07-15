package com.charliesbot.kanshu.core.reader.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderMargins
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
      val storedBoldness =
        prefs[BOLDNESS_KEY]?.coerceIn(
          ReaderPreferences.BOLDNESS_MIN,
          ReaderPreferences.BOLDNESS_MAX,
        ) ?: ReaderPreferences().boldness
      val storedMargins =
        prefs[MARGINS_KEY]?.let(::marginsFromStorage) ?: ReaderPreferences().margins
      val storedAlignment =
        prefs[ALIGNMENT_KEY]?.let(::alignmentFromStorage) ?: ReaderPreferences().alignment
      val storedLineSpacing =
        prefs[LINE_SPACING_KEY]?.coerceIn(
          ReaderPreferences.LINE_SPACING_MIN,
          ReaderPreferences.LINE_SPACING_MAX,
        ) ?: ReaderPreferences().lineSpacing
      val storedParagraphSpacing =
        prefs[PARAGRAPH_SPACING_KEY]?.coerceIn(
          ReaderPreferences.PARAGRAPH_SPACING_MIN,
          ReaderPreferences.PARAGRAPH_SPACING_MAX,
        ) ?: ReaderPreferences().paragraphSpacing
      val storedWordSpacing =
        prefs[WORD_SPACING_KEY]?.coerceIn(
          ReaderPreferences.WORD_SPACING_MIN,
          ReaderPreferences.WORD_SPACING_MAX,
        ) ?: ReaderPreferences().wordSpacing
      val storedLetterSpacing =
        prefs[LETTER_SPACING_KEY]?.coerceIn(
          ReaderPreferences.LETTER_SPACING_MIN,
          ReaderPreferences.LETTER_SPACING_MAX,
        ) ?: ReaderPreferences().letterSpacing
      ReaderPreferences(
        font = storedFont,
        fontScale = storedScale,
        boldness = storedBoldness,
        margins = storedMargins,
        alignment = storedAlignment,
        lineSpacing = storedLineSpacing,
        paragraphSpacing = storedParagraphSpacing,
        wordSpacing = storedWordSpacing,
        letterSpacing = storedLetterSpacing,
      )
    }

  override suspend fun setFont(font: ReaderFont) {
    dataStore.edit { it[FONT_KEY] = font.name }
  }

  override suspend fun setFontScale(scale: Float) {
    val clamped = scale.coerceIn(ReaderPreferences.SCALE_MIN, ReaderPreferences.SCALE_MAX)
    dataStore.edit { it[SCALE_KEY] = clamped }
  }

  override suspend fun setBoldness(value: Float) {
    val clamped = value.coerceIn(ReaderPreferences.BOLDNESS_MIN, ReaderPreferences.BOLDNESS_MAX)
    dataStore.edit { it[BOLDNESS_KEY] = clamped }
  }

  override suspend fun setMargins(margins: ReaderMargins) {
    dataStore.edit { it[MARGINS_KEY] = margins.name }
  }

  override suspend fun setAlignment(alignment: ReaderAlignment) {
    dataStore.edit { it[ALIGNMENT_KEY] = alignment.name }
  }

  override suspend fun setLineSpacing(value: Float) {
    val clamped =
      value.coerceIn(ReaderPreferences.LINE_SPACING_MIN, ReaderPreferences.LINE_SPACING_MAX)
    dataStore.edit { it[LINE_SPACING_KEY] = clamped }
  }

  override suspend fun setParagraphSpacing(value: Float) {
    val clamped =
      value.coerceIn(
        ReaderPreferences.PARAGRAPH_SPACING_MIN,
        ReaderPreferences.PARAGRAPH_SPACING_MAX,
      )
    dataStore.edit { it[PARAGRAPH_SPACING_KEY] = clamped }
  }

  override suspend fun setWordSpacing(value: Float) {
    val clamped =
      value.coerceIn(ReaderPreferences.WORD_SPACING_MIN, ReaderPreferences.WORD_SPACING_MAX)
    dataStore.edit { it[WORD_SPACING_KEY] = clamped }
  }

  override suspend fun setLetterSpacing(value: Float) {
    val clamped =
      value.coerceIn(ReaderPreferences.LETTER_SPACING_MIN, ReaderPreferences.LETTER_SPACING_MAX)
    dataStore.edit { it[LETTER_SPACING_KEY] = clamped }
  }

  override suspend fun resetSpacing() {
    dataStore.edit {
      it.remove(LINE_SPACING_KEY)
      it.remove(PARAGRAPH_SPACING_KEY)
      it.remove(WORD_SPACING_KEY)
      it.remove(LETTER_SPACING_KEY)
    }
  }

  // Unknown stored names (e.g. an enum entry removed in a later version) fall back to the default
  // rather than throwing on Flow collection.
  private fun fontFromStorage(name: String): ReaderFont =
    ReaderFont.entries.firstOrNull { it.name == name } ?: ReaderPreferences().font

  private fun marginsFromStorage(name: String): ReaderMargins =
    ReaderMargins.entries.firstOrNull { it.name == name } ?: ReaderPreferences().margins

  private fun alignmentFromStorage(name: String): ReaderAlignment =
    ReaderAlignment.entries.firstOrNull { it.name == name } ?: ReaderPreferences().alignment

  private companion object {
    val FONT_KEY = stringPreferencesKey("font")
    val SCALE_KEY = floatPreferencesKey("font_scale")
    val BOLDNESS_KEY = floatPreferencesKey("boldness")
    val MARGINS_KEY = stringPreferencesKey("margins")
    val ALIGNMENT_KEY = stringPreferencesKey("alignment")
    val LINE_SPACING_KEY = floatPreferencesKey("line_spacing")
    val PARAGRAPH_SPACING_KEY = floatPreferencesKey("paragraph_spacing")
    val WORD_SPACING_KEY = floatPreferencesKey("word_spacing")
    val LETTER_SPACING_KEY = floatPreferencesKey("letter_spacing")
  }
}
