package com.charliesbot.kanshu.core.library

import android.util.Log
import com.charliesbot.kanshu.core.database.dao.BookDao
import com.charliesbot.kanshu.core.database.entity.BookEntity
import com.charliesbot.kanshu.core.provider.BookId
import com.charliesbot.kanshu.core.provider.ProviderBookKey
import com.charliesbot.kanshu.core.provider.ProviderCover
import com.charliesbot.kanshu.core.provider.ProviderError
import com.charliesbot.kanshu.core.provider.ProviderInstanceId
import com.charliesbot.kanshu.core.provider.ProviderRegistry
import com.charliesbot.kanshu.core.provider.ProviderResult
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookRepositoryImpl(
  private val providers: ProviderRegistry,
  private val booksDir: File,
  private val bookDao: BookDao,
  // Long-lived. Default uses Dispatchers.IO + SupervisorJob so one failed download doesn't
  // kill the scope; production callers don't need to pass this.
  private val downloadScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : BookRepository {

  init {
    booksDir.mkdirs()
    sweepOrphanTmpFiles()
    // Catches the rare case where the DB row outlives the file (e.g. crash between file delete
    // and clearDownload). Uninstall wipes both stores together so this is bounded to in-process
    // bugs we control.
    downloadScope.launch { reconcileDownloads() }
  }

  // Ephemeral runtime state: in-flight download progress, keyed by stable book identity. Persists
  // nothing — completed downloads land in the DB; abandoned downloads disappear with the process.
  private val _inFlight = MutableStateFlow<Map<BookId, Int>>(emptyMap())

  override fun observeBooks(): Flow<LibraryResult> = flow {
    val localBooksSnapshot =
      try {
        bookDao.getAll()
      } catch (e: Exception) {
        Log.w(TAG, "Failed to query local database cache", e)
        emit(LibraryResult.Error.Unknown)
        return@flow
      }

    val enabledProviders = providers.enabledProviders()
    val enabledProviderIds = enabledProviders.mapTo(mutableSetOf()) { it.descriptor.id.value }
    val failures = mutableListOf<ProviderError>()
    var hadSuccessfulRefresh = false
    enabledProviders.forEach { provider ->
      when (val result = provider.fetchCatalog()) {
        is ProviderResult.Success -> {
          hadSuccessfulRefresh = true
          val remoteBooks =
            result.value.map { book ->
              BookEntity(
                id = bookIdFor(book.key),
                providerInstanceId = book.key.providerId.value,
                providerItemId = book.key.providerItemId,
                title = book.title,
                localPath = null,
                byteSize = null,
                downloadedAt = null,
                lastOpenedAt = null,
                coverToken = book.revisionToken,
              )
            }
          bookDao.syncBooks(
            providerInstanceId = provider.descriptor.id.value,
            remoteBooks = remoteBooks,
            fetchedIds = remoteBooks.mapTo(mutableSetOf()) { it.id },
          )
        }
        is ProviderResult.Failure -> {
          failures += result.error
          Log.w(TAG, "Failed to refresh provider ${provider.descriptor.id.value}: ${result.error}")
        }
      }
    }
    val hasUsableBooks = localBooksSnapshot.any { it.providerInstanceId in enabledProviderIds }
    if (!hasUsableBooks && !hadSuccessfulRefresh && failures.isNotEmpty()) {
      emit(failures.toLibraryError())
      return@flow
    }

    emitAll(
      bookDao.observeAll().combine(_inFlight) { dbBooks, inFlight ->
        val items =
          dbBooks
            .filter { it.providerInstanceId in enabledProviderIds }
            .map { entity ->
              val provider = providers.provider(ProviderInstanceId(entity.providerInstanceId))
              val coverUrl =
                (provider.resolveCover(entity.providerBookKey(), entity.coverToken)
                    as? ProviderCover.RemoteUrl)
                  ?.value
              LibraryItem(
                bookId = BookId(entity.id),
                title = entity.title,
                coverUrl = coverUrl,
                downloadState =
                  when {
                    inFlight.containsKey(BookId(entity.id)) ->
                      DownloadState.Downloading(progress = inFlight.getValue(BookId(entity.id)))
                    entity.localPath != null -> DownloadState.Downloaded
                    else -> DownloadState.NotDownloaded
                  },
              )
            }
        if (items.isEmpty()) LibraryResult.Empty else LibraryResult.Success(items)
      }
    )
  }

  override fun download(item: LibraryItem) {
    val bookId = item.bookId
    if (_inFlight.value.containsKey(bookId)) return
    // Atomic check-and-set on the in-flight map: two rapid taps must not both launch a download
    // for the same book. The first one to add _inFlight[bookId] wins; the second
    // returns above. We also gate on "already downloaded" by reading the DB inside the worker
    // before doing any network work — cheap and avoids a race with a near-simultaneous upsert.
    var shouldStart = false
    _inFlight.update { current ->
      if (current.containsKey(bookId)) current
      else {
        shouldStart = true
        current + (bookId to 0)
      }
    }
    if (shouldStart) downloadScope.launch { runDownload(item) }
  }

  override fun delete(bookId: BookId) {
    if (_inFlight.value.containsKey(bookId)) return
    downloadScope.launch {
      // Clear the DB row first so UI observers see NotDownloaded before the bytes are gone.
      // Avoids a window where the UI still says Downloaded but the file is already missing.
      bookDao.clearDownload(bookId.value)
      bookFile(bookId).delete()
      tmpFile(bookId).delete()
    }
  }

  override suspend fun fileFor(bookId: BookId): File? {
    val row = bookDao.find(bookId.value) ?: return null
    val path = row.localPath ?: return null
    val file = File(path)
    return file.takeIf { it.exists() && it.length() > 0 }
  }

  private suspend fun runDownload(item: LibraryItem) {
    val bookId = item.bookId
    val tmp = tmpFile(bookId)
    // Clean any stale tmp from a previous failed attempt before we start writing.
    tmp.delete()
    try {
      // Defensive: if a row already claims the file is downloaded, skip. Handles a race between
      // the download() guard and a parallel reconciliation/sync writing the row.
      val existing = bookDao.find(bookId.value)
      if (existing?.localPath != null && File(existing.localPath).exists()) {
        _inFlight.update { it - bookId }
        return
      }
      val existingBook =
        existing
          ?: run {
            _inFlight.update { it - bookId }
            return
          }
      val providerId = ProviderInstanceId(existingBook.providerInstanceId)
      val provider = providers.provider(providerId)
      val result =
        provider.acquire(existingBook.providerBookKey(), tmp) { bytesSoFar, totalBytes ->
          val pct =
            if (totalBytes != null && totalBytes > 0) {
              ((bytesSoFar * 100) / totalBytes).toInt().coerceIn(0, 100)
            } else 0
          // Throttle to integer-percent changes — e-ink can't keep up with per-chunk emissions.
          _inFlight.update { current ->
            val currentPct = current[bookId] ?: return@update current
            if (currentPct == pct) current else current + (bookId to pct)
          }
        }
      if (result is ProviderResult.Failure) {
        Log.w(TAG, "Provider failed to acquire ${bookId.value}: ${result.error}")
        tmp.delete()
        _inFlight.update { it - bookId }
        return
      }
      // ATOMIC_MOVE + REPLACE_EXISTING gives a single-step replacement so fileFor() never
      // observes a missing file in the window between delete+rename. Same-filesystem move on
      // filesDir reduces to rename(2) which is atomic on the underlying FS.
      val finalFile = bookFile(bookId)
      Files.move(
        tmp.toPath(),
        finalFile.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
      )
      bookDao.upsert(
        BookEntity(
          id = bookId.value,
          providerInstanceId = existingBook.providerInstanceId,
          providerItemId = existingBook.providerItemId,
          title = item.title,
          localPath = finalFile.absolutePath,
          byteSize = finalFile.length(),
          downloadedAt = System.currentTimeMillis(),
          lastOpenedAt = null,
          coverToken = existingBook.coverToken,
        )
      )
      _inFlight.update { it - bookId }
    } catch (e: CancellationException) {
      tmp.delete()
      _inFlight.update { it - bookId }
      throw e
    } catch (e: IOException) {
      Log.w(TAG, "Download IO failed for ${bookId.value}", e)
      tmp.delete()
      _inFlight.update { it - bookId }
    } catch (e: Exception) {
      Log.w(TAG, "Download failed for ${bookId.value}", e)
      tmp.delete()
      _inFlight.update { it - bookId }
    }
  }

  private fun sweepOrphanTmpFiles() {
    booksDir.listFiles()?.forEach { f -> if (f.isFile && f.name.endsWith(".epub.tmp")) f.delete() }
  }

  private suspend fun reconcileDownloads() {
    bookDao.allDownloaded().forEach { row ->
      val path = row.localPath ?: return@forEach
      if (!File(path).exists()) bookDao.clearDownload(row.id)
    }
  }

  private fun bookIdFor(key: ProviderBookKey): String =
    "${key.providerId.value}:${key.providerItemId}"

  private fun bookFile(bookId: BookId) = File(booksDir, "${managedFileStem(bookId)}.epub")

  private fun tmpFile(bookId: BookId) = File(booksDir, "${managedFileStem(bookId)}.epub.tmp")

  private fun managedFileStem(bookId: BookId): String =
    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bookId.value.toByteArray())

  private companion object {
    const val TAG = "BookRepository"
  }
}

private fun BookEntity.providerBookKey() =
  ProviderBookKey(ProviderInstanceId(providerInstanceId), providerItemId)

private fun List<ProviderError>.toLibraryError(): LibraryResult =
  when {
    any { it == ProviderError.NoCredentials } -> LibraryResult.NoCredentials
    any { it == ProviderError.Unauthorized } -> LibraryResult.Error.Unauthorized
    any { it == ProviderError.Network } -> LibraryResult.Error.Network
    any { it == ProviderError.MalformedResponse } -> LibraryResult.Error.UnexpectedResponse
    else -> LibraryResult.Error.Unknown
  }
