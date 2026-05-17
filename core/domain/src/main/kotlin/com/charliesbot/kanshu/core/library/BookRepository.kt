package com.charliesbot.kanshu.core.library

import java.io.File
import kotlinx.coroutines.flow.Flow

interface BookRepository {
  // Emits the Kavita snapshot overlaid with current local download state. Re-emits whenever a
  // download progresses, completes, or is deleted — the snapshot itself is fetched once per
  // collection.
  fun observeBooks(): Flow<LibraryResult>

  // Fire-and-forget: starts a download in a repo-internal long-lived scope so navigating away
  // from the library doesn't cancel it. Idempotent — already-downloading or downloaded series
  // are no-ops.
  fun download(seriesId: Int)

  // Removes the local file. Safe to call on a not-downloaded series. No-op while a download is
  // in flight (the UI gates this; the guard is defensive).
  fun delete(seriesId: Int)

  // Returns the on-disk EPUB for a downloaded series, or null. Used by the reader source.
  fun fileFor(seriesId: Int): File?
}
