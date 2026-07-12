package com.charliesbot.kanshu.core.sync

import android.util.Log
import com.charliesbot.kanshu.core.database.dao.ReadingProgressDao
import com.charliesbot.kanshu.core.database.entity.ReadingProgressEntity
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import com.charliesbot.kanshu.core.reader.progress.progressionIn
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.readium.r2.shared.publication.Publication

private const val TAG = "SyncRepository"
private const val PUSH_DEBOUNCE_MILLIS = 5_000L
private const val FLUSH_PUSH_TIMEOUT_MILLIS = 4_000L

// Orchestrates the reader's relationship with both stores: the local DB (always-on, always
// current) and the remote sync (best-effort, online-only). The reader VM calls these methods;
// everything else (locator serialization, debounce timing, decision logic) lives here.
interface SyncRepository {
  // Called once when the reader opens. Returns either the local resume position, or
  // PromptForRemote if the server has a position written after our local one — in which case
  // the VM shows the "Continue from page X on (device)?" dialog and lets the user choose.
  suspend fun resolveInitialPosition(
    bookId: String,
    file: File,
    publication: Publication,
  ): InitialPosition

  // Called on every ReaderPosition change. Writes locally immediately, schedules a debounced
  // push. Calling again before the debounce expires cancels the previous push and reschedules.
  fun setProgress(bookId: String, file: File, position: ReaderPosition, publication: Publication)

  // Called from the reader VM's onCleared. Cancels the pending debounce and force-pushes the
  // last position synchronously, with a short timeout so a hanging network doesn't block book
  // teardown. Best-effort: failure is logged and ignored — the DB row is the source of truth
  // and the next book open will retry.
  suspend fun flushProgress(
    bookId: String,
    file: File,
    position: ReaderPosition,
    publication: Publication,
  )

  // Manual "Sync to Furthest Page Read" action. Returns the remote progress only if it's
  // further along than the local position; null means "already at furthest" or "no remote yet."
  suspend fun pullFurthestPosition(
    bookId: String,
    file: File,
    publication: Publication,
  ): RemoteProgress?
}

sealed interface InitialPosition {
  // Apply this position (or just start at the beginning if null). No prompt.
  data class UseLocal(val position: ReaderPosition?) : InitialPosition

  // Show a dialog. local is what we'd use if the user declines; remote is the suggested
  // alternative.
  data class PromptForRemote(val local: ReaderPosition?, val remote: RemoteProgress) :
    InitialPosition
}

class SyncRepositoryImpl(
  private val progressSync: ProgressSync,
  private val progressDao: ReadingProgressDao,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SyncRepository {

  // One reader is open at a time, so we only need one pending push slot. If the user somehow
  // navigated between two books fast enough to overlap, the second setProgress would cancel
  // the first's pending push — acceptable: the DB write already happened, and the second push
  // covers the second book's position.
  private var pendingPush: Job? = null

  private val jsonSerializer = Json { ignoreUnknownKeys = true }

  override suspend fun resolveInitialPosition(
    bookId: String,
    file: File,
    publication: Publication,
  ): InitialPosition {
    val local = progressDao.find(bookId)
    val localPosition = local?.locatorJson?.let { decodePosition(it) }
    val remote =
      progressSync.pull(file, publication).getOrNull()
        ?: return InitialPosition.UseLocal(localPosition)
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

  override fun setProgress(
    bookId: String,
    file: File,
    position: ReaderPosition,
    publication: Publication,
  ) {
    val now = System.currentTimeMillis()
    val locatorJson = jsonSerializer.encodeToString(ReaderPosition.serializer(), position)
    val progression = position.progressionIn(publication)
    scope.launch {
      progressDao.upsert(
        ReadingProgressEntity(
          bookId = bookId,
          locatorJson = locatorJson,
          progression = progression,
          updatedAt = now,
          syncMetadata = null,
        )
      )
    }
    pendingPush?.cancel()
    pendingPush = scope.launch {
      delay(PUSH_DEBOUNCE_MILLIS)
      push(file, position, publication)
    }
  }

  override suspend fun flushProgress(
    bookId: String,
    file: File,
    position: ReaderPosition,
    publication: Publication,
  ) {
    pendingPush?.cancel()
    pendingPush = null
    withTimeoutOrNull(FLUSH_PUSH_TIMEOUT_MILLIS) { push(file, position, publication) }
  }

  override suspend fun pullFurthestPosition(
    bookId: String,
    file: File,
    publication: Publication,
  ): RemoteProgress? {
    val localProgression = progressDao.find(bookId)?.progression ?: 0.0
    val remote = progressSync.pull(file, publication).getOrNull() ?: return null
    return remote.takeIf { it.percentage > localProgression }
  }

  private suspend fun push(file: File, position: ReaderPosition, publication: Publication) {
    val result = progressSync.push(file, position, publication, System.currentTimeMillis())
    result.exceptionOrNull()?.let { Log.w(TAG, "Push failed (will retry on next save): $it") }
  }

  private fun decodePosition(json: String): ReaderPosition =
    try {
      jsonSerializer.decodeFromString(ReaderPosition.serializer(), json)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to decode local ReaderPosition: $e")
      ReaderPosition(schemaVersion = 1, spineIndex = 0, pageIndex = 0, progressInSpine = 0f)
    }
}
