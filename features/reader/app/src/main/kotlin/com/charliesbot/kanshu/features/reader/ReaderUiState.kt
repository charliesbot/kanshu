package com.charliesbot.kanshu.features.reader

import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import org.readium.r2.shared.publication.Publication

sealed interface ReaderUiState {
  data object Loading : ReaderUiState

  // [initialPosition] is the local resume position (or null for "start of book"). If the sync
  // layer also surfaced a remote suggestion, it's exposed separately via the VM's
  // remoteSuggestion StateFlow so the screen can show the prompt.
  // [initialPreferences] reflects the persisted reader prefs at the moment the navigator is
  // first mounted — subsequent changes flow through settings injection from the screen.
  data class Ready(
    val title: String?,
    val publication: Publication,
    val initialPosition: ReaderPosition?,
    val initialPreferences: ReaderPreferences,
    val currentChapter: CachedResource,
  ) : ReaderUiState

  sealed interface Error : ReaderUiState {
    data object NotFound : Error

    data object ParseFailed : Error

    data object ReadFailed : Error
  }
}
