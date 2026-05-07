package com.charliesbot.kanshu.features.reader

sealed interface ReaderUiState {
  data object Loading : ReaderUiState

  // pageCount is null until the WebView's JS bridge reports it after layout. currentPageIndex
  // is 0 by default but bumps to (pageCount - 1) when the user came in from the next chapter
  // via Prev (so the previous chapter opens on its last page, not its first).
  data class Ready(
    val title: String?,
    val chapterCount: Int,
    val currentChapterIndex: Int,
    val currentHtml: String,
    val currentPageIndex: Int = 0,
    val pageCount: Int? = null,
  ) : ReaderUiState

  sealed interface Error : ReaderUiState {
    data object NotFound : Error

    data object ParseFailed : Error

    data object ReadFailed : Error
  }
}
