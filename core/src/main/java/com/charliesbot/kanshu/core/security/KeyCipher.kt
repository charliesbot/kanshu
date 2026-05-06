package com.charliesbot.kanshu.core.security

interface KeyCipher {
  fun encrypt(plaintext: String): String

  fun decrypt(encoded: String): String?
}
