package com.charliesbot.kanshu.features.reader

import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalReadiumApi::class)
sealed interface ReaderUiState {
  data object Loading : ReaderUiState

  // [initialLocator] is the local resume position (or null for "start of book"). If the sync
  // layer also surfaced a remote suggestion, it's exposed separately via the VM's
  // remoteSuggestion StateFlow so the screen can show the prompt.
  data class Ready(
    val title: String?,
    val factory: EpubNavigatorFactory,
    val initialLocator: Locator?,
  ) : ReaderUiState

  sealed interface Error : ReaderUiState {
    data object NotFound : Error

    data object ParseFailed : Error

    data object ReadFailed : Error
  }
}
