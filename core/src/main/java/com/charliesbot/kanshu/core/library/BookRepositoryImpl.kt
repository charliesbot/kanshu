package com.charliesbot.kanshu.core.library

import android.util.Log
import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.connection.KavitaCredentials
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaException
import com.charliesbot.kanshu.core.kavita.dto.SeriesDto
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "BookRepository"
private const val DEFAULT_PAGE_SIZE = 100

class BookRepositoryImpl(
  private val credentialsRepository: CredentialsRepository,
  private val api: KavitaApi,
  private val booksDir: File,
  // Long-lived. Default uses Dispatchers.IO + SupervisorJob so one failed download doesn't
  // kill the scope; production callers don't need to pass this.
  private val downloadScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : BookRepository {

  init {
    booksDir.mkdirs()
    sweepOrphanTmpFiles()
  }

  // Source of truth for local download state. Seeded from a directory scan so re-launches pick
  // up files written by previous sessions. Tmp files (.epub.tmp) are deliberately ignored:
  // they represent in-flight or aborted downloads, not a completed asset.
  private val _downloads = MutableStateFlow(scanExistingDownloads())

  override fun observeBooks(): Flow<LibraryResult> =
    flow { emit(loadFromKavita()) }
      .combine(_downloads) { snapshot, states -> overlay(snapshot, states) }

  override fun download(seriesId: Int) {
    // Atomic check-and-set: two rapid taps must not both launch a download for the same series.
    // The first one to flip the state to Downloading(0) wins; the second observes Downloading
    // and bails. Without this guard, both invocations would observe NotDownloaded and race on
    // the same tmp file.
    var shouldStart = false
    _downloads.update { current ->
      val state = current[seriesId]
      if (state is DownloadState.Downloading || state == DownloadState.Downloaded) {
        current
      } else {
        shouldStart = true
        current + (seriesId to DownloadState.Downloading(progress = 0))
      }
    }
    if (shouldStart) downloadScope.launch { runDownload(seriesId) }
  }

  override fun delete(seriesId: Int) {
    if (_downloads.value[seriesId] is DownloadState.Downloading) return
    bookFile(seriesId).delete()
    tmpFile(seriesId).delete()
    setState(seriesId, DownloadState.NotDownloaded)
  }

  override fun fileFor(seriesId: Int): File? {
    val f = bookFile(seriesId)
    return if (f.exists() && f.length() > 0) f else null
  }

  private suspend fun runDownload(seriesId: Int) {
    // Downloading(0) was already set atomically by download(); don't re-set here.
    val tmp = tmpFile(seriesId)
    // Clean any stale tmp from a previous failed attempt before we start writing.
    tmp.delete()
    try {
      val creds = credentialsRepository.credentials.first()
      if (creds == null) {
        setState(seriesId, DownloadState.NotDownloaded)
        return
      }
      val chapterId = resolveFirstChapterId(creds, seriesId)
      if (chapterId == null) {
        setState(seriesId, DownloadState.NotDownloaded)
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
        val state = _downloads.value[seriesId]
        if (state is DownloadState.Downloading && state.progress == pct) return@downloadChapter
        setState(seriesId, DownloadState.Downloading(progress = pct))
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
      setState(seriesId, DownloadState.Downloaded)
    } catch (e: CancellationException) {
      tmp.delete()
      throw e
    } catch (e: KavitaException) {
      Log.w(TAG, "Download failed for $seriesId: $e")
      tmp.delete()
      setState(seriesId, DownloadState.NotDownloaded)
    } catch (e: IOException) {
      Log.w(TAG, "Download IO failed for $seriesId", e)
      tmp.delete()
      setState(seriesId, DownloadState.NotDownloaded)
    } catch (e: Exception) {
      Log.w(TAG, "Download failed for $seriesId", e)
      tmp.delete()
      setState(seriesId, DownloadState.NotDownloaded)
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

  private suspend fun loadFromKavita(): LibraryResult {
    val creds = credentialsRepository.credentials.first() ?: return LibraryResult.NoCredentials
    return try {
      val series =
        api.listSeries(
          baseUrl = creds.baseUrl,
          apiKey = creds.apiKey,
          pageNumber = 1,
          pageSize = DEFAULT_PAGE_SIZE,
        )
      val items = series.map { it.toLibraryItem(creds) }
      if (items.isEmpty()) LibraryResult.Empty else LibraryResult.Success(items)
    } catch (e: CancellationException) {
      throw e
    } catch (e: KavitaException) {
      e.toLibraryError()
    }
  }

  private fun overlay(result: LibraryResult, states: Map<Int, DownloadState>): LibraryResult =
    when (result) {
      is LibraryResult.Success ->
        LibraryResult.Success(
          result.items.map { it.copy(downloadState = states[it.id] ?: DownloadState.NotDownloaded) }
        )
      else -> result
    }

  private fun sweepOrphanTmpFiles() {
    booksDir.listFiles()?.forEach { f -> if (f.isFile && f.name.endsWith(".epub.tmp")) f.delete() }
  }

  private fun scanExistingDownloads(): Map<Int, DownloadState> =
    booksDir
      .listFiles()
      ?.asSequence()
      ?.filter { it.isFile && it.name.endsWith(".epub") && it.length() > 0 }
      ?.mapNotNull { f ->
        val id = f.name.removeSuffix(".epub").toIntOrNull() ?: return@mapNotNull null
        id to DownloadState.Downloaded
      }
      ?.toMap() ?: emptyMap()

  private fun setState(seriesId: Int, state: DownloadState) {
    _downloads.update { current ->
      if (state == DownloadState.NotDownloaded) current - seriesId
      else current + (seriesId to state)
    }
  }

  private fun bookFile(seriesId: Int) = File(booksDir, "$seriesId.epub")

  private fun tmpFile(seriesId: Int) = File(booksDir, "$seriesId.epub.tmp")
}

private fun SeriesDto.toLibraryItem(credentials: KavitaCredentials): LibraryItem =
  LibraryItem(
    id = id,
    title = name,
    coverUrl = coverImage?.let { buildCoverUrl(credentials.baseUrl, id, credentials.apiKey) },
  )

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
