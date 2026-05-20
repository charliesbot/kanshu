package com.charliesbot.kanshu.core.library.usecase

import com.charliesbot.kanshu.core.library.BookRepository
import com.charliesbot.kanshu.core.library.LibraryResult
import kotlinx.coroutines.flow.Flow

class LoadLibraryUseCase(private val repository: BookRepository) {
  operator fun invoke(): Flow<LibraryResult> = repository.observeBooks()
}
