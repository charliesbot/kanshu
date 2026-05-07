package com.charliesbot.kanshu.features.reader

import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
sealed interface ReaderUiState {
  data object Loading : ReaderUiState

  data class Ready(val title: String?, val factory: EpubNavigatorFactory) : ReaderUiState

  sealed interface Error : ReaderUiState {
    data object NotFound : Error

    data object ParseFailed : Error

    data object ReadFailed : Error
  }
}
