package com.charliesbot.kanshu.core.reader.preferences

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.readerPreferencesDataStore by preferencesDataStore(name = "reader_preferences")
