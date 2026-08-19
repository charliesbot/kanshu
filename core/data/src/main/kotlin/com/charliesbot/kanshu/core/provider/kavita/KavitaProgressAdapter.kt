package com.charliesbot.kanshu.core.provider.kavita

import android.util.Log
import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaException
import com.charliesbot.kanshu.core.kavita.dto.KoreaderBookDto
import com.charliesbot.kanshu.core.kosync.KoreaderHash
import com.charliesbot.kanshu.core.kosync.KoreaderPosition
import com.charliesbot.kanshu.core.provider.RemoteProgress
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import com.charliesbot.kanshu.core.reader.progress.progressionIn
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication

internal class KavitaProgressAdapter(
  private val api: KavitaApi,
  private val credentials: CredentialsRepository,
) {

  suspend fun push(
    file: File,
    position: ReaderPosition,
    publication: Publication,
    timestampMillis: Long,
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      val creds =
        credentials.credentials.first() ?: return@withContext Result.failure(NoCredentialsException)
      val hash =
        KoreaderHash.ofFile(file) ?: return@withContext Result.failure(MissingFileException)
      val payload =
        KoreaderBookDto(
          document = hash,
          // Kavita ignores these KOReader compatibility fields.
          device_id = KANSHU_DEVICE_ID,
          device = KANSHU_DEVICE_NAME,
          percentage = position.progressionIn(publication).toFloat(),
          progress = KoreaderPosition.encode(position.spineIndex),
          // Kavita's controller ignores the inbound timestamp and stamps its own UTC clock
          // (KoreaderProgressUpdateDto sets Timestamp = DateTime.UtcNow). We still send the
          // epoch seconds the kosync protocol expects.
          timestamp = timestampMillis / 1000,
        )
      runCatchingNetwork { api.putKoreaderProgress(creds.baseUrl, creds.apiKey, payload) }
    }

  suspend fun pull(file: File, publication: Publication): Result<RemoteProgress?> =
    withContext(Dispatchers.IO) {
      val creds =
        credentials.credentials.first() ?: return@withContext Result.failure(NoCredentialsException)
      val hash =
        KoreaderHash.ofFile(file) ?: return@withContext Result.failure(MissingFileException)
      runCatchingNetwork {
        val remote =
          api.getKoreaderProgress(creds.baseUrl, creds.apiKey, hash)
            ?: return@runCatchingNetwork null
        val spineIndex = KoreaderPosition.decodeSpineIndex(remote.progress)
        if (spineIndex == null) {
          Log.w(TAG, "Remote progress had no decodable spine index; percentage only")
        }
        RemoteProgress(
          // kosync carries only a spine-level position, so the offset within the chapter is
          // unknown — resume lands at the chapter start.
          position =
            spineIndex?.let {
              ReaderPosition(spineIndex = it, charOffset = 0, progressInSpine = 0f)
            },
          percentage = remote.percentage.toDouble(),
          // KOReader's kosync protocol uses epoch seconds; we expose millis everywhere else.
          timestampMillis = remote.timestamp * 1000L,
        )
      }
    }

  private inline fun <T> runCatchingNetwork(block: () -> T): Result<T> =
    try {
      Result.success(block())
    } catch (e: CancellationException) {
      throw e
    } catch (e: KavitaException) {
      Result.failure(e)
    } catch (e: Exception) {
      Result.failure(e)
    }

  private companion object {
    const val TAG = "KavitaProgressAdapter"
    const val KANSHU_DEVICE_ID = "kanshu"
    const val KANSHU_DEVICE_NAME = "Kanshu"
  }
}

// Sentinel exceptions so callers can distinguish "skip, no setup" from "retry later."
object NoCredentialsException : RuntimeException("No Kavita credentials configured")

object MissingFileException : RuntimeException("Book file is missing")
