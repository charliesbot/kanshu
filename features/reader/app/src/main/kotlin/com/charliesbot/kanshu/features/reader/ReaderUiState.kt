package com.charliesbot.kanshu.features.reader

sealed interface ReaderUiState {
  data object Loading : ReaderUiState

  data class Ready(
    val title: String?,
    val chapterCount: Int,
    val currentIndex: Int,
    val currentHtml: String,
  ) : ReaderUiState

  sealed interface Error : ReaderUiState {
    data object NotFound : Error

    data object ParseFailed : Error

    data object ReadFailed : Error
  }
}
