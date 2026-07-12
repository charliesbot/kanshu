package com.charliesbot.kanshu.core.reader

import kotlinx.coroutines.flow.Flow

interface ReaderPreferencesRepository {
  val preferences: Flow<ReaderPreferences>

  suspend fun setFont(font: ReaderFont)

  suspend fun setFontScale(scale: Float)

  suspend fun setMargins(margins: ReaderMargins)

  suspend fun setAlignment(alignment: ReaderAlignment)

  suspend fun setLineSpacing(value: Float)

  suspend fun setParagraphSpacing(value: Float)

  suspend fun setWordSpacing(value: Float)

  suspend fun setLetterSpacing(value: Float)

  suspend fun resetSpacing()
}
