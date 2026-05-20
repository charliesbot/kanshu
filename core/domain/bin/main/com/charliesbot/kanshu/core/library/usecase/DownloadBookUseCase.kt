package com.charliesbot.kanshu.core.library.usecase

import com.charliesbot.kanshu.core.library.BookRepository
import com.charliesbot.kanshu.core.library.LibraryItem

class DownloadBookUseCase(private val repository: BookRepository) {
  operator fun invoke(item: LibraryItem) = repository.download(item)
}
