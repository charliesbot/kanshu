package com.charliesbot.kanshu.core.reader.usecase

import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.ReaderSource

class OpenBookUseCase(private val source: ReaderSource) {
  suspend operator fun invoke(seriesId: Int): ReaderResult = source.openBook(seriesId)
}
