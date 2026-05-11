package com.charliesbot.kanshu.core.library.usecase

import com.charliesbot.kanshu.core.library.BookRepository

class DeleteDownloadUseCase(private val repository: BookRepository) {
  operator fun invoke(seriesId: Int) = repository.delete(seriesId)
}
