package com.charliesbot.kanshu.core.reader

import kotlinx.coroutines.flow.Flow

interface ReaderPreferencesRepository {
  val preferences: Flow<ReaderPreferences>

  suspend fun setFont(font: ReaderFont)

  suspend fun setFontScale(scale: Float)

  suspend fun setMargins(margins: ReaderMargins)

  suspend fun setAlignment(alignment: ReaderAlignment)
}
