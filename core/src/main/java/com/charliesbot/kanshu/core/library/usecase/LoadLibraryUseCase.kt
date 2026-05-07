package com.charliesbot.kanshu.core.library.usecase

import com.charliesbot.kanshu.core.library.LibraryRepository
import com.charliesbot.kanshu.core.library.LibraryResult

class LoadLibraryUseCase(private val repository: LibraryRepository) {
  suspend operator fun invoke(): LibraryResult = repository.loadLibrary()
}
