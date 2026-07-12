package com.charliesbot.kanshu.core.library

import android.util.Log
import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.connection.KavitaCredentials
import com.charliesbot.kanshu.core.database.dao.BookDao
import com.charliesbot.kanshu.core.database.entity.BookEntity
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaException
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "BookRepository"
private const val DEFAULT_PAGE_SIZE = 100
private const val SOURCE_KAVITA = "kavita"

class BookRepositoryImpl(
  private val credentialsRepository: CredentialsRepository,
  private val api: KavitaApi,
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

  // Ephemeral runtime state: in-flight download progress, keyed by Kavita seriesId. Persists
  // nothing — completed downloads land in the DB; abandoned downloads disappear with the process.
  private val _inFlight = MutableStateFlow<Map<Int, Int>>(emptyMap())

  override fun observeBooks(): Flow<LibraryResult> = flow {
    val creds = credentialsRepository.credentials.first()
    if (creds == null) {
      emit(LibraryResult.NoCredentials)
      return@flow
    }

    val localBooksSnapshot =
      try {
        bookDao.getAll()
      } catch (e: Exception) {
        Log.w(TAG, "Failed to query local database cache", e)
        emit(LibraryResult.Error.Unknown)
        return@flow
      }

    try {
      val series =
        api.listSeries(
          baseUrl = creds.baseUrl,
          apiKey = creds.apiKey,
          pageNumber = 1,
          pageSize = DEFAULT_PAGE_SIZE,
        )
      val fetchedIds = series.map { bookIdFor(it.id) }.toSet()

      val remoteBooks = series.map { s ->
        val bookId = bookIdFor(s.id)
        BookEntity(
          id = bookId,
          source = SOURCE_KAVITA,
          sourceItemId = s.id.toString(),
          title = s.name,
          localPath = null,
          byteSize = null,
          downloadedAt = null,
          lastOpenedAt = null,
          coverToken = s.coverImage,
        )
      }

      bookDao.syncBooks(SOURCE_KAVITA, remoteBooks, fetchedIds)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Failed to sync library with Kavita", e)
      // Offline-first design choice: if the cache is populated, we silently fall back to it
      // rather than presenting a network error to the user.
      if (localBooksSnapshot.isEmpty()) {
        val err = if (e is KavitaException) e.toLibraryError() else LibraryResult.Error.Unknown
        emit(err)
        return@flow
      }
    }

    emitAll(
      bookDao.observeAll().combine(_inFlight) { dbBooks, inFlight ->
        val items =
          dbBooks
            .filter { it.source == SOURCE_KAVITA }
            .map { entity ->
              val seriesId = seriesIdFromBookId(entity.id) ?: 0
              val coverUrl =
                if (entity.coverToken != null) {
                  buildCoverUrl(creds.baseUrl, seriesId, creds.apiKey)
                } else {
                  null
                }
              LibraryItem(
                id = seriesId,
                title = entity.title,
                coverUrl = coverUrl,
                downloadState =
                  when {
                    inFlight.containsKey(seriesId) ->
                      DownloadState.Downloading(progress = inFlight.getValue(seriesId))
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
    val seriesId = item.id
    if (_inFlight.value.containsKey(seriesId)) return
    // Atomic check-and-set on the in-flight map: two rapid taps must not both launch a download
    // for the same series. The first one to flip _inFlight[seriesId] to 0 wins; the second
    // returns above. We also gate on "already downloaded" by reading the DB inside the worker
    // before doing any network work — cheap and avoids a race with a near-simultaneous upsert.
    var shouldStart = false
    _inFlight.update { current ->
      if (current.containsKey(seriesId)) current
      else {
        shouldStart = true
        current + (seriesId to 0)
      }
    }
    if (shouldStart) downloadScope.launch { runDownload(item) }
  }

  override fun delete(seriesId: Int) {
    if (_inFlight.value.containsKey(seriesId)) return
    downloadScope.launch {
      // Clear the DB row first so UI observers see NotDownloaded before the bytes are gone.
      // Avoids a window where the UI still says Downloaded but the file is already missing.
      bookDao.clearDownload(bookIdFor(seriesId))
      bookFile(seriesId).delete()
      tmpFile(seriesId).delete()
    }
  }

  override suspend fun fileFor(seriesId: Int): File? {
    val row = bookDao.find(bookIdFor(seriesId)) ?: return null
    val path = row.localPath ?: return null
    val file = File(path)
    return file.takeIf { it.exists() && it.length() > 0 }
  }

  private suspend fun runDownload(item: LibraryItem) {
    val seriesId = item.id
    val tmp = tmpFile(seriesId)
    // Clean any stale tmp from a previous failed attempt before we start writing.
    tmp.delete()
    try {
      val creds = credentialsRepository.credentials.first()
      if (creds == null) {
        _inFlight.update { it - seriesId }
        return
      }
      // Defensive: if a row already claims the file is downloaded, skip. Handles a race between
      // the download() guard and a parallel reconciliation/sync writing the row.
      val existing = bookDao.find(bookIdFor(seriesId))
      if (existing?.localPath != null && File(existing.localPath).exists()) {
        _inFlight.update { it - seriesId }
        return
      }
      val chapterId = resolveFirstChapterId(creds, seriesId)
      if (chapterId == null) {
        _inFlight.update { it - seriesId }
        return
      }
      api.downloadChapter(
        baseUrl = creds.baseUrl,
        apiKey = creds.apiKey,
        chapterId = chapterId,
        target = tmp,
      ) { bytesSoFar, totalBytes ->
        val pct =
          if (totalBytes != null && totalBytes > 0) {
            ((bytesSoFar * 100) / totalBytes).toInt().coerceIn(0, 100)
          } else 0
        // Throttle to integer-percent changes — e-ink can't keep up with per-chunk emissions.
        _inFlight.update { current ->
          val currentPct = current[seriesId] ?: return@update current
          if (currentPct == pct) current else current + (seriesId to pct)
        }
      }
      // ATOMIC_MOVE + REPLACE_EXISTING gives a single-step replacement so fileFor() never
      // observes a missing file in the window between delete+rename. Same-filesystem move on
      // filesDir reduces to rename(2) which is atomic on the underlying FS.
      val finalFile = bookFile(seriesId)
      Files.move(
        tmp.toPath(),
        finalFile.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
      )
      bookDao.upsert(
        BookEntity(
          id = bookIdFor(seriesId),
          source = SOURCE_KAVITA,
          sourceItemId = seriesId.toString(),
          title = item.title,
          localPath = finalFile.absolutePath,
          byteSize = finalFile.length(),
          downloadedAt = System.currentTimeMillis(),
          lastOpenedAt = null,
          coverToken = existing?.coverToken,
        )
      )
      _inFlight.update { it - seriesId }
    } catch (e: CancellationException) {
      tmp.delete()
      _inFlight.update { it - seriesId }
      throw e
    } catch (e: KavitaException) {
      Log.w(TAG, "Download failed for $seriesId: $e")
      tmp.delete()
      _inFlight.update { it - seriesId }
    } catch (e: IOException) {
      Log.w(TAG, "Download IO failed for $seriesId", e)
      tmp.delete()
      _inFlight.update { it - seriesId }
    } catch (e: Exception) {
      Log.w(TAG, "Download failed for $seriesId", e)
      tmp.delete()
      _inFlight.update { it - seriesId }
    }
  }

  // Kavita's model: Series → Volume → Chapter → file. EPUB libraries are typically one chapter
  // per series (see docs/KAVITA_API.md), so taking the first chapter is the Phase 0 contract.
  // We sort volumes by id so multi-volume series resolve deterministically rather than
  // relying on server response order.
  private suspend fun resolveFirstChapterId(creds: KavitaCredentials, seriesId: Int): Int? {
    val volumes = api.listVolumes(creds.baseUrl, creds.apiKey, seriesId)
    return volumes
      .sortedBy { it.id }
      .asSequence()
      .flatMap { it.chapters.asSequence() }
      .firstOrNull()
      ?.id
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

  private fun bookIdFor(seriesId: Int): String = "$SOURCE_KAVITA:$seriesId"

  private fun seriesIdFromBookId(bookId: String): Int? =
    if (bookId.startsWith("$SOURCE_KAVITA:")) bookId.removePrefix("$SOURCE_KAVITA:").toIntOrNull()
    else null

  private fun bookFile(seriesId: Int) = File(booksDir, "$seriesId.epub")

  private fun tmpFile(seriesId: Int) = File(booksDir, "$seriesId.epub.tmp")
}

// Kavita's image endpoints take the api key as a query param so the URL is usable as an <img src>.
// Encode both values: an api key may contain reserved characters (& + = #) that would otherwise
// break the URL.
private fun buildCoverUrl(baseUrl: String, seriesId: Int, apiKey: String): String {
  val encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())
  return "${baseUrl.trimEnd('/')}/api/Image/series-cover?seriesId=$seriesId&apiKey=$encodedKey"
}

private fun KavitaException.toLibraryError(): LibraryResult.Error =
  when (this) {
    KavitaException.Unauthorized -> LibraryResult.Error.Unauthorized
    KavitaException.NetworkError -> LibraryResult.Error.Network
    KavitaException.UnexpectedResponse -> LibraryResult.Error.UnexpectedResponse
    is KavitaException.Unknown -> LibraryResult.Error.Unknown
  }
