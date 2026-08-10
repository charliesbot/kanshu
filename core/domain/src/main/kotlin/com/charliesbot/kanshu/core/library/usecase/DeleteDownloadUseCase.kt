package com.charliesbot.kanshu.core.library.usecase

import com.charliesbot.kanshu.core.library.BookRepository
import com.charliesbot.kanshu.core.provider.BookId

class DeleteDownloadUseCase(private val repository: BookRepository) {
  operator fun invoke(bookId: BookId) = repository.delete(bookId)
}
