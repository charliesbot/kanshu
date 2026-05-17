package com.charliesbot.kanshu.core.connection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.charliesbot.kanshu.core.security.KeyCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CredentialsRepositoryImpl(
  private val dataStore: DataStore<Preferences>,
  private val cipher: KeyCipher,
) : CredentialsRepository {
  override val credentials: Flow<KavitaCredentials?> =
    dataStore.data.map { prefs ->
      val baseUrl = prefs[BASE_URL_KEY] ?: return@map null
      val encryptedApiKey = prefs[API_KEY_KEY] ?: return@map null
      val apiKey = cipher.decrypt(encryptedApiKey) ?: return@map null
      KavitaCredentials(baseUrl, apiKey)
    }

  override suspend fun save(credentials: KavitaCredentials) {
    val encryptedApiKey = cipher.encrypt(credentials.apiKey)
    dataStore.edit { prefs ->
      prefs[BASE_URL_KEY] = credentials.baseUrl
      prefs[API_KEY_KEY] = encryptedApiKey
    }
  }

  override suspend fun clear() {
    dataStore.edit { it.clear() }
  }

  private companion object {
    val BASE_URL_KEY = stringPreferencesKey("kavita_base_url")
    val API_KEY_KEY = stringPreferencesKey("kavita_api_key_encrypted")
  }
}
