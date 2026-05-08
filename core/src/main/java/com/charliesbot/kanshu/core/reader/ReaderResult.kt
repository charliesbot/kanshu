package com.charliesbot.kanshu.core.reader

import org.readium.r2.shared.publication.Publication

sealed interface ReaderResult {
  // Caller is responsible for closing the publication when done (the reader feature's VM does
  // this in onCleared). The original BookHandle wrapper became degenerate once the feature
  // module had to import Readium types anyway for EpubNavigatorFactory.
  data class Success(val publication: Publication) : ReaderResult

  sealed interface Error : ReaderResult {
    data object NotFound : Error

    data object ParseFailed : Error

    data object ReadFailed : Error
  }
}
