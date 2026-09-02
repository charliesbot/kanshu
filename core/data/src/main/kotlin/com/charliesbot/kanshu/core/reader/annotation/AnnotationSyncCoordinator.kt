package com.charliesbot.kanshu.core.reader.annotation

import android.util.Log
import com.charliesbot.kanshu.core.database.dao.BookDao
import com.charliesbot.kanshu.core.provider.BookId
import com.charliesbot.kanshu.core.provider.Provider
import com.charliesbot.kanshu.core.provider.ProviderBookContext
import com.charliesbot.kanshu.core.provider.ProviderBookKey
import com.charliesbot.kanshu.core.provider.ProviderHighlightContext
import com.charliesbot.kanshu.core.provider.ProviderInstanceId
import com.charliesbot.kanshu.core.provider.ProviderRegistry
import com.charliesbot.kanshu.core.provider.ProviderResult
import com.charliesbot.kanshu.core.provider.ProviderSourceMap
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.readium.r2.shared.publication.Publication

interface AnnotationSyncCoordinator {
  suspend fun supports(bookId: BookId): Boolean

  suspend fun synchronize(
    bookId: BookId,
    file: File,
    publication: Publication,
    sourceMapForSpine: suspend (Int) -> ProviderSourceMap?,
  )
}

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
    return providers
      .provider(ProviderInstanceId(book.providerInstanceId))
      .descriptor
      .capabilities
      .highlightSync
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
    val provider = providers.provider(ProviderInstanceId(book.providerInstanceId))
    if (!provider.descriptor.capabilities.highlightSync) return
    val context =
      ProviderHighlightContext(
        book =
          ProviderBookContext(
            book =
              ProviderBookKey(
                providerId = ProviderInstanceId(book.providerInstanceId),
                providerItemId = book.providerItemId,
              ),
            file = request.file,
            publication = request.publication,
            providerMetadata =
              book.providerMetadata
                ?.let {
                  runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrNull()
                }
                .orEmpty(),
          ),
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
          when (state) {
            HighlightSyncState.PENDING_DELETE ->
              annotations.acknowledgeDelete(change.localId, change.expectedUpdatedAt)
            HighlightSyncState.PENDING_UPSERT ->
              annotations.acknowledgeUpsert(
                change.localId,
                change.expectedUpdatedAt,
                result.value.remoteId,
              )
            HighlightSyncState.SYNCED -> Unit
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
