package com.charliesbot.kanshu.core.reader.usecase

import com.charliesbot.kanshu.core.provider.BookId
import com.charliesbot.kanshu.core.reader.EpubOpener
import com.charliesbot.kanshu.core.reader.ReaderResult

class OpenBookUseCase(private val opener: EpubOpener) {
  suspend operator fun invoke(bookId: BookId): ReaderResult = opener.openBook(bookId)
}
