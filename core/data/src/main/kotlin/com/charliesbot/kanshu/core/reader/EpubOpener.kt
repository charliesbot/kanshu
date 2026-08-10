package com.charliesbot.kanshu.core.reader

import com.charliesbot.kanshu.core.provider.BookId

interface EpubOpener {
  suspend fun openBook(bookId: BookId): ReaderResult
}
