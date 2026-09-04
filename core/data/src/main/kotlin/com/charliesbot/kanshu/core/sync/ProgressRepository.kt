package com.charliesbot.kanshu.core.sync

import android.util.Log
import com.charliesbot.kanshu.core.database.dao.BookDao
import com.charliesbot.kanshu.core.database.dao.ReadingProgressDao
import com.charliesbot.kanshu.core.database.entity.ReadingProgressEntity
import com.charliesbot.kanshu.core.provider.BookId
import com.charliesbot.kanshu.core.provider.ProviderBookContext
import com.charliesbot.kanshu.core.provider.ProviderBookKey
import com.charliesbot.kanshu.core.provider.ProviderError
import com.charliesbot.kanshu.core.provider.ProviderInstanceId
import com.charliesbot.kanshu.core.provider.ProviderRegistry
import com.charliesbot.kanshu.core.provider.ProviderResult
import com.charliesbot.kanshu.core.provider.RemoteProgress
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import com.charliesbot.kanshu.core.reader.progress.progressionIn
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.readium.r2.shared.publication.Publication

/**
 * Orchestrates the reader's relationship with both stores: the local DB (always-on, always current)
 * and the remote sync (best-effort, online-only). The reader VM calls these methods; everything
 * else — locator serialization, debounce timing, decision logic — lives here.
 */
interface ProgressRepository {
  /**
   * The position to open at, resolved against the server.
   *
   * Returns [InitialPosition.UseLocal], or [InitialPosition.PromptForRemote] when the server has a
   * position written after our local one — in which case the VM shows the "Continue from page X on
   * (device)?" dialog and lets the user choose.
   */
  suspend fun resolveInitialPosition(
    bookId: BookId,
    file: File,
    publication: Publication,
  ): InitialPosition

  /**
   * Local-only resume lookup: decodes the DB row, with no network involved.
   *
   * A row written by the page-index build decodes to the chapter start via the `charOffset`
   * default, so there is no upgrade step. The reader opens through this rather than
   * [resolveInitialPosition] so an unreachable Kavita host can't hold the first page behind an HTTP
   * timeout — acting on a newer remote position needs the prompt above, which doesn't exist yet.
   */
  suspend fun localPosition(bookId: BookId): ReaderPosition?

  /**
   * Records where the reader is. Writes locally immediately and schedules a debounced push; calling
   * again before the debounce expires cancels the previous push and reschedules.
   *
   * A push that would overwrite a further-along remote is skipped, so callers do not need to track
   * whether the reader has actually moved.
   */
  fun setProgress(bookId: BookId, file: File, position: ReaderPosition, publication: Publication)

  /**
   * Teardown flush: cancels the pending debounce and force-pushes the last position synchronously,
   * with a short timeout so a hanging network doesn't block book teardown. Best-effort — failure is
   * logged and ignored, since the DB row is the source of truth and the next book open will retry.
   *
   * Nothing calls this yet. The debounce lives on this repository's own scope rather than the
   * reader's, so a position survives the ViewModel being torn down as long as the process does;
   * this exists for the case where it doesn't.
   */
  suspend fun flushProgress(
    bookId: BookId,
    file: File,
    position: ReaderPosition,
    publication: Publication,
  )

  /**
   * Manual "Sync to Furthest Page Read" action. Returns the remote progress only if it is further
   * along than the local position; null means "already at furthest" or "no remote yet".
   */
  suspend fun pullFurthestPosition(
    bookId: BookId,
    file: File,
    publication: Publication,
  ): RemoteProgress?
}

sealed interface InitialPosition {
  /** Apply this position, or just start at the beginning when null. No prompt. */
  data class UseLocal(val position: ReaderPosition?) : InitialPosition

  /**
   * Show a dialog. [local] is what we'd use if the user declines; [remote] is the suggested
   * alternative.
   */
  data class PromptForRemote(val local: ReaderPosition?, val remote: RemoteProgress) :
    InitialPosition
}

class ProgressRepositoryImpl(
  private val providers: ProviderRegistry,
  private val books: BookDao,
  private val progressDao: ReadingProgressDao,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ProgressRepository {

  // One reader is open at a time, so we only need one pending push slot. If the user somehow
  // navigated between two books fast enough to overlap, the second setProgress would cancel
  // the first's pending push — acceptable: the DB write already happened, and the second push
  // covers the second book's position.
  private var pendingPush: Job? = null

  private val jsonSerializer = Json { ignoreUnknownKeys = true }

  override suspend fun resolveInitialPosition(
    bookId: BookId,
    file: File,
    publication: Publication,
  ): InitialPosition {
    val local = progressDao.find(bookId.value)
    val localPosition = local?.locatorJson?.let { decodePosition(it) }
    val remote =
      pullRemote(bookId, file, publication) ?: return InitialPosition.UseLocal(localPosition)
    // Prompt criterion: server saw a write more recently than we did. Furthest-vs-current is
    // a separate manual action (see pullFurthestPosition) — auto-pull just surfaces "another
    // device touched this book after you did" without judging which position is better.
    val localTimestamp = local?.updatedAt ?: 0L
    return if (remote.timestampMillis > localTimestamp) {
      InitialPosition.PromptForRemote(local = localPosition, remote = remote)
    } else {
      InitialPosition.UseLocal(localPosition)
    }
  }

  override suspend fun localPosition(bookId: BookId): ReaderPosition? =
    progressDao.find(bookId.value)?.locatorJson?.let(::decodePosition)

  override fun setProgress(
    bookId: BookId,
    file: File,
    position: ReaderPosition,
    publication: Publication,
  ) {
    val now = System.currentTimeMillis()
    val locatorJson = jsonSerializer.encodeToString(ReaderPosition.serializer(), position)
    val progression = position.progressionIn(publication)
    scope.launch {
      // Caught rather than allowed to reach the uncaught handler: this runs on a bare scope with
      // no exception handler, so a full disk or a missing parent book row would take the app down
      // mid-read. Losing one progress write is recoverable; the next page turn rewrites it.
      try {
        progressDao.upsert(
          ReadingProgressEntity(
            bookId = bookId.value,
            locatorJson = locatorJson,
            progression = progression,
            updatedAt = now,
          )
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Local progress write failed (will retry on next save): $e")
      }
    }
    pendingPush?.cancel()
    pendingPush = scope.launch {
      delay(PUSH_DEBOUNCE_MILLIS)
      push(bookId, file, position, publication)
    }
  }

  override suspend fun flushProgress(
    bookId: BookId,
    file: File,
    position: ReaderPosition,
    publication: Publication,
  ) {
    pendingPush?.cancel()
    pendingPush = null
    withTimeoutOrNull(FLUSH_PUSH_TIMEOUT_MILLIS) { push(bookId, file, position, publication) }
  }

  override suspend fun pullFurthestPosition(
    bookId: BookId,
    file: File,
    publication: Publication,
  ): RemoteProgress? {
    val localProgression = progressDao.find(bookId.value)?.progression ?: 0.0
    val remote = pullRemote(bookId, file, publication) ?: return null
    return remote.takeIf { it.percentage > localProgression }
  }

  private suspend fun push(
    bookId: BookId,
    file: File,
    position: ReaderPosition,
    publication: Publication,
  ) {
    // The remote overwrites unconditionally, so reading ahead elsewhere and then turning one page
    // here would destroy it. Fails open: a pull that errors must not block syncing on a device
    // that is usually offline. Suppresses deliberate backward navigation too, which is the
    // accepted cost until the "continue from another device?" prompt replaces this.
    val remote =
      try {
        pullRemote(bookId, file, publication)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        null
      }
    if (remote != null && remote.percentage > position.progressionIn(publication)) {
      Log.d(TAG, "Skipping push: remote is further along")
      return
    }
    val result =
      try {
        pushRemote(bookId, file, position, publication)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Normalized rather than left to propagate: Provider implementations do work outside
        // their ProviderResult boundary (credential reads, file hashing), so a book file that
        // vanishes between the exists() check and the open throws instead of returning a failure.
        // These pushes run on a bare scope with no exception handler, so that would reach the
        // default uncaught handler and kill the app mid-read.
        ProviderResult.Failure(ProviderError.Unknown(e.message))
      }
    when (result) {
      is ProviderResult.Failure ->
        Log.w(TAG, "Push failed (will retry on next save): ${result.error}")
      is ProviderResult.Success -> Log.d(TAG, "Progress push succeeded")
    }
  }

  private suspend fun pullRemote(
    bookId: BookId,
    file: File,
    publication: Publication,
  ): RemoteProgress? {
    val (provider, context) = providerContext(bookId, file, publication) ?: return null
    return when (val result = provider.pullProgress(context)) {
      is ProviderResult.Success -> result.value
      is ProviderResult.Failure -> null
    }
  }

  private suspend fun pushRemote(
    bookId: BookId,
    file: File,
    position: ReaderPosition,
    publication: Publication,
  ): ProviderResult<Unit> {
    val (provider, context) =
      providerContext(bookId, file, publication) ?: return ProviderResult.Success(Unit)
    return provider.pushProgress(context, position)
  }

  private suspend fun providerContext(
    bookId: BookId,
    file: File,
    publication: Publication,
  ) =
    books.find(bookId.value)?.let { book ->
      val provider = providers.provider(ProviderInstanceId(book.providerInstanceId))
      provider to
        ProviderBookContext(
          book =
            ProviderBookKey(
              providerId = ProviderInstanceId(book.providerInstanceId),
              providerItemId = book.providerItemId,
            ),
          file = file,
          publication = publication,
          providerMetadata = decodeProviderMetadata(book.providerMetadata),
        )
    }

  private fun decodePosition(json: String): ReaderPosition =
    try {
      jsonSerializer.decodeFromString(ReaderPosition.serializer(), json)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to decode local ReaderPosition: $e")
      ReaderPosition(spineIndex = 0, charOffset = 0, progressInSpine = 0f)
    }

  private companion object {
    const val TAG = "ProgressRepository"
    const val PUSH_DEBOUNCE_MILLIS = 5_000L
    // Covers a pull and a push, since every push checks the remote first.
    const val FLUSH_PUSH_TIMEOUT_MILLIS = 6_000L
  }
}

private fun decodeProviderMetadata(value: String?): Map<String, String> =
  value
    ?.let {
      runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(it) }
        .getOrNull()
    }
    .orEmpty()
