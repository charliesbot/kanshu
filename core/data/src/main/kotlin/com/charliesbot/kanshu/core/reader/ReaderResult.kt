package com.charliesbot.kanshu.core.reader

import java.io.File
import org.readium.r2.shared.publication.Publication

sealed interface ReaderResult {
  // Caller is responsible for closing the publication when done (the reader feature's VM does
  // this in onCleared). The original BookHandle wrapper became degenerate once the feature
  // module had to import Readium types anyway for EpubNavigatorFactory.
  //
  // [file] is the on-disk EPUB the publication was opened from. It's surfaced here so the VM
  // can pass it to the sync layer (kosync hashes the file content to identify the book) without
  // needing a separate round-trip through BookRepository.
  data class Success(val publication: Publication, val file: File) : ReaderResult

  sealed interface Error : ReaderResult {
    data object NotFound : Error

    data object ParseFailed : Error

    data object ReadFailed : Error
  }
}
