package com.charliesbot.kanshu.core.reader

sealed interface ReaderResult {
  data class Success(val handle: BookHandle) : ReaderResult

  sealed interface Error : ReaderResult {
    data object NotFound : Error

    data object ParseFailed : Error

    data object ReadFailed : Error
  }
}
