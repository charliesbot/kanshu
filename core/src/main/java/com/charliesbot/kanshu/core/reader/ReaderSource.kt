package com.charliesbot.kanshu.core.reader

interface ReaderSource {
  suspend fun openBook(seriesId: Int): ReaderResult
}
