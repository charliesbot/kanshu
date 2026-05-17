package com.charliesbot.kanshu.core.connection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.charliesbot.kanshu.core.security.KeyCipher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialsRepositoryImplTest {

  private val baseUrlKey = stringPreferencesKey("kavita_base_url")
  private val apiKeyKey = stringPreferencesKey("kavita_api_key_encrypted")

  @Test
  fun `credentials returns null when nothing stored`() = runTest {
    val store = FakeDataStore()
    val repo = CredentialsRepositoryImpl(store, ReversingCipher())

    assertNull(repo.credentials.first())
  }

  @Test
  fun `save then read returns decrypted credentials`() = runTest {
    val store = FakeDataStore()
    val repo = CredentialsRepositoryImpl(store, ReversingCipher())

    repo.save(KavitaCredentials("https://kavita.example.com", "secret"))

    assertEquals(
      KavitaCredentials("https://kavita.example.com", "secret"),
      repo.credentials.first(),
    )
  }

  @Test
  fun `decrypt failure surfaces null`() = runTest {
    val store = FakeDataStore()
    store.edit { prefs ->
      prefs[baseUrlKey] = "https://kavita.example.com"
      prefs[apiKeyKey] = "garbage"
    }
    val repo = CredentialsRepositoryImpl(store, FailingCipher)

    assertNull(repo.credentials.first())
  }

  @Test
  fun `clear removes stored credentials`() = runTest {
    val store = FakeDataStore()
    val repo = CredentialsRepositoryImpl(store, ReversingCipher())
    repo.save(KavitaCredentials("https://kavita.example.com", "secret"))

    repo.clear()

    assertNull(repo.credentials.first())
  }

  private class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(preferencesOf())
    override val data = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
      val updated = transform(state.value)
      state.value = updated
      return updated
    }
  }

  private class ReversingCipher : KeyCipher {
    override fun encrypt(plaintext: String): String = plaintext.reversed()

    override fun decrypt(encoded: String): String? = encoded.reversed()
  }

  private object FailingCipher : KeyCipher {
    override fun encrypt(plaintext: String): String = plaintext

    override fun decrypt(encoded: String): String? = null
  }
}
