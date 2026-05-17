package com.charliesbot.kanshu.core.connection

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.kavitaCredentialsDataStore by preferencesDataStore(name = "kavita_credentials")
