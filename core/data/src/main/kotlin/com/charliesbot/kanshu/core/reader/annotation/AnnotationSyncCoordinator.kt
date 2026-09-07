package com.charliesbot.kanshu.core.reader.annotation

import android.util.Log
import com.charliesbot.kanshu.core.database.dao.BookDao
import com.charliesbot.kanshu.core.database.entity.toProviderBookContext
import com.charliesbot.kanshu.core.database.entity.toProviderBookKey
import com.charliesbot.kanshu.core.provider.BookId
import com.charliesbot.kanshu.core.provider.HighlightChange
import com.charliesbot.kanshu.core.provider.Provider
import com.charliesbot.kanshu.core.provider.ProviderHighlightContext
import com.charliesbot.kanshu.core.provider.ProviderRegistry
import com.charliesbot.kanshu.core.provider.ProviderResult
import com.charliesbot.kanshu.core.provider.ProviderSourceMap
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.readium.r2.shared.publication.Publication

/** Coordinates on-demand highlight synchronization independently of local persistence. */
interface AnnotationSyncCoordinator {
  /**
   * Checks declared provider support, not network reachability; returns false for a missing book.
   */
  suspend fun supports(bookId: BookId): Boolean

  /**
   * Requests a sync round for an opened book: deletes, upserts, then a complete pull. Overlapping
   * calls return after replacing the queued request with the latest one. Provider failure results
   * leave pending changes untouched; no background retry is scheduled.
   */
  suspend fun synchronize(
    bookId: BookId,
    file: File,
    publication: Publication,
    sourceMapForSpine: suspend (Int) -> ProviderSourceMap?,
  )
}

/** Serializes sync rounds and coalesces overlapping triggers into the latest queued request. */
class AnnotationSyncCoordinatorImpl(
  private val providers: ProviderRegistry,
  private val books: BookDao,
  private val annotations: AnnotationRepository,
) : AnnotationSyncCoordinator {
  private val stateMutex = Mutex()
  private var running = false
  private var pending: SyncRequest? = null

  override suspend fun supports(bookId: BookId): Boolean {
    val book = books.find(bookId.value) ?: return false
    val provider = providers.provider(book.toProviderBookKey().providerId)
    return provider.descriptor.capabilities.highlightSync
  }

  override suspend fun synchronize(
    bookId: BookId,
    file: File,
    publication: Publication,
    sourceMapForSpine: suspend (Int) -> ProviderSourceMap?,
  ) {
    var request = SyncRequest(bookId, file, publication, sourceMapForSpine)
    val shouldRun = stateMutex.withLock {
      if (running) {
        pending = request
        false
      } else {
        running = true
        true
      }
    }
    if (!shouldRun) return

    while (true) {
      runRound(request)
      val next =
        stateMutex.withLock {
          pending.also {
            pending = null
            if (it == null) running = false
          }
        } ?: return
      request = next
    }
  }

  private suspend fun runRound(request: SyncRequest) {
    val book = books.find(request.bookId.value) ?: return
    val provider = providers.provider(book.toProviderBookKey().providerId)
    if (!provider.descriptor.capabilities.highlightSync) return
    val context =
      ProviderHighlightContext(
        book = book.toProviderBookContext(request.file, request.publication),
        sourceMapForSpine = request.sourceMapForSpine,
      )

    pushPending(request.bookId, provider, context, HighlightSyncState.PENDING_DELETE)
    pushPending(request.bookId, provider, context, HighlightSyncState.PENDING_UPSERT)
    when (val pulled = provider.pullHighlights(context)) {
      is ProviderResult.Success -> annotations.applySnapshot(request.bookId.value, pulled.value)
      is ProviderResult.Failure -> Log.w(TAG, "Highlight pull failed: " + pulled.error)
    }
  }

  private suspend fun pushPending(
    bookId: BookId,
    provider: Provider,
    context: ProviderHighlightContext,
    state: HighlightSyncState,
  ) {
    annotations.pendingChanges(bookId.value, state).forEach { change ->
      when (val result = provider.pushHighlight(context, change)) {
        is ProviderResult.Success ->
          when (change) {
            is HighlightChange.Delete ->
              annotations.acknowledgeDelete(change.localId, change.expectedUpdatedAt)
            is HighlightChange.Upsert ->
              annotations.acknowledgeUpsert(
                change.localId,
                change.expectedUpdatedAt,
                result.value.remoteId,
              )
          }
        is ProviderResult.Failure -> Log.w(TAG, "Highlight push failed: " + result.error)
      }
    }
  }

  private data class SyncRequest(
    val bookId: BookId,
    val file: File,
    val publication: Publication,
    val sourceMapForSpine: suspend (Int) -> ProviderSourceMap?,
  )

  private companion object {
    const val TAG = "AnnotationSync"
  }
}
