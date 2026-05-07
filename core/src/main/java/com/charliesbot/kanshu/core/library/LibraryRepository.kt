package com.charliesbot.kanshu.core.library

interface LibraryRepository {
  suspend fun loadLibrary(): LibraryResult
}
