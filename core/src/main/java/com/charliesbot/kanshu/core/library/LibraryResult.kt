package com.charliesbot.kanshu.core.library

sealed interface LibraryResult {
  data class Success(val items: List<LibraryItem>) : LibraryResult

  data object Empty : LibraryResult

  data object NoCredentials : LibraryResult

  sealed interface Error : LibraryResult {
    data object Unauthorized : Error

    data object Network : Error

    data object UnexpectedResponse : Error

    data object Unknown : Error
  }
}
