package com.charliesbot.kanshu.core.library

import com.charliesbot.kanshu.core.provider.BookId
import java.io.File
import kotlinx.coroutines.flow.Flow

interface BookRepository {
  // Refreshes enabled providers independently, then emits their cached catalogs overlaid with
  // current local download state. Re-emits whenever a download progresses, completes, or is
  // deleted.
  fun observeBooks(): Flow<LibraryResult>

  // Fire-and-forget: starts a download in a repo-internal long-lived scope so navigating away
  // from the library doesn't cancel it. Idempotent — already-downloading or downloaded books
  // are no-ops. Takes the full LibraryItem so the local DB row can record the title; the UI
  // already has the item in hand at tap time.
  fun download(item: LibraryItem)

  // Removes the local file and clears the DB row's download metadata. Safe to call on a
  // not-downloaded book. No-op while a download is in flight (the UI gates this; the guard
  // is defensive).
  fun delete(bookId: BookId)

  // Returns the on-disk EPUB for a downloaded book, or null. Suspend because the lookup
  // hits the DB now — the FS is no longer authoritative for "is this downloaded."
  suspend fun fileFor(bookId: BookId): File?
}
