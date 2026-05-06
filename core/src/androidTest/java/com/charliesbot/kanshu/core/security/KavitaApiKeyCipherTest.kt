package com.charliesbot.kanshu.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KavitaApiKeyCipherTest {

  private val cipher = KavitaApiKeyCipher()

  @Test
  fun roundTrip() {
    val plaintext = "kavita-api-key-abcdef123456"
    val encoded = cipher.encrypt(plaintext)
    assertEquals(plaintext, cipher.decrypt(encoded))
  }

  @Test
  fun decryptGarbageReturnsNull() {
    assertNull(cipher.decrypt("not-base64-or-anything-real"))
  }

  @Test
  fun encryptProducesDifferentCiphertextEachCall() {
    val plaintext = "kavita-api-key"
    val a = cipher.encrypt(plaintext)
    val b = cipher.encrypt(plaintext)
    assert(a != b) { "GCM IV should be fresh per encryption" }
  }
}
