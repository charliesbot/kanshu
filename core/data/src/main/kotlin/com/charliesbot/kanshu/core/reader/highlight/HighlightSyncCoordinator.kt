package com.charliesbot.kanshu.core.reader.highlight

import android.util.Log
import com.charliesbot.kanshu.core.database.dao.BookDao
import com.charliesbot.kanshu.core.database.entity.toProviderBookContext
import com.charliesbot.kanshu.core.database.entity.toProviderBookKey
import com.charliesbot.kanshu.core.provider.BookId
import com.charliesbot.kanshu.core.provider.EpubSourceMap
import com.charliesbot.kanshu.core.provider.HighlightChange
import com.charliesbot.kanshu.core.provider.Provider
import com.charliesbot.kanshu.core.provider.ProviderHighlightContext
import com.charliesbot.kanshu.core.provider.ProviderRegistry
import com.charliesbot.kanshu.core.provider.ProviderResult
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication

/** Coordinates on-demand highlight synchronization independently of local persistence. */
interface HighlightSyncCoordinator {
  /**
   * Checks declared provider support, not network reachability; returns false for a missing book.
   */
  suspend fun supports(bookId: BookId): Boolean

  /**
   * Requests a sync round for an opened book: deletes, upserts, then a complete pull. Overlapping
   * calls return after replacing the queued request with the latest one. Provider failure results
   * leave pending changes untouched; no background retry is scheduled. Cancellation and thrown
   * exceptions propagate and discard queued reader contexts; a future trigger can retry.
   */
  suspend fun synchronize(
    bookId: BookId,
    file: File,
    publication: Publication,
    sourceMapForSpine: suspend (Int) -> EpubSourceMap?,
  )
}

/** Serializes sync rounds and coalesces overlapping triggers into the latest queued request. */
class HighlightSyncCoordinatorImpl(
  private val providers: ProviderRegistry,
  private val books: BookDao,
  private val highlights: HighlightRepository,
) : HighlightSyncCoordinator {
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
    sourceMapForSpine: suspend (Int) -> EpubSourceMap?,
  ) {
    val initialRequest = SyncRequest(bookId, file, publication, sourceMapForSpine)
    if (!startOrQueue(initialRequest)) return

    try {
      var request: SyncRequest? = initialRequest
      while (request != null) {
        runRound(request)
        request = takeNextRequest()
      }
    } catch (failure: Throwable) {
      // Release ownership even if the caller was cancelled while holding a queued request.
      // Mutations remain in Room; a future trigger retries with a live reader context.
      withContext(NonCancellable) {
        stateMutex.withLock {
          pending = null
          running = false
        }
      }
      throw failure
    }
  }

  private suspend fun startOrQueue(request: SyncRequest): Boolean = stateMutex.withLock {
    if (running) {
      pending = request
      return@withLock false
    }
    running = true
    true
  }

  private suspend fun takeNextRequest(): SyncRequest? = stateMutex.withLock {
    val next = pending
    pending = null
    if (next == null) running = false
    next
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
      is ProviderResult.Success -> highlights.applySnapshot(request.bookId.value, pulled.value)
      is ProviderResult.Failure -> Log.w(TAG, "Highlight pull failed: " + pulled.error)
    }
  }

  private suspend fun pushPending(
    bookId: BookId,
    provider: Provider,
    context: ProviderHighlightContext,
    state: HighlightSyncState,
  ) {
    highlights.pendingChanges(bookId.value, state).forEach { change ->
      when (val result = provider.pushHighlight(context, change)) {
        is ProviderResult.Success -> acknowledge(change, result.value.remoteId)
        is ProviderResult.Failure -> Log.w(TAG, "Highlight push failed: " + result.error)
      }
    }
  }

  private suspend fun acknowledge(change: HighlightChange, remoteId: String?) {
    when (change) {
      is HighlightChange.Delete ->
        highlights.acknowledgeDelete(change.localId, change.expectedUpdatedAt)
      is HighlightChange.Upsert ->
        highlights.acknowledgeUpsert(change.localId, change.expectedUpdatedAt, remoteId)
    }
  }

  private data class SyncRequest(
    val bookId: BookId,
    val file: File,
    val publication: Publication,
    val sourceMapForSpine: suspend (Int) -> EpubSourceMap?,
  )

  private companion object {
    const val TAG = "HighlightSync"
  }
}
