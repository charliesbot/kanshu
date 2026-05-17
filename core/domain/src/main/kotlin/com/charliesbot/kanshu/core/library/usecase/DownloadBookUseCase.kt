package com.charliesbot.kanshu.core.library.usecase

import com.charliesbot.kanshu.core.library.BookRepository

class DownloadBookUseCase(private val repository: BookRepository) {
  operator fun invoke(seriesId: Int) = repository.download(seriesId)
}
