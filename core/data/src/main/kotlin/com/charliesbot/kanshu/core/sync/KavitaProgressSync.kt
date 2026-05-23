package com.charliesbot.kanshu.core.sync

import android.util.Log
import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaException
import com.charliesbot.kanshu.core.kavita.dto.KoreaderBookDto
import com.charliesbot.kanshu.core.kosync.KoreaderHash
import com.charliesbot.kanshu.core.kosync.KoreaderPosition
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import com.charliesbot.kanshu.core.reader.progress.progressionIn
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication

private const val TAG = "KavitaProgressSync"

class KavitaProgressSync(
  private val api: KavitaApi,
  private val credentials: CredentialsRepository,
  private val device: DeviceIdentity,
) : ProgressSync {

  override suspend fun push(
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
          device_id = device.id,
          device = device.name,
          percentage = position.progressionIn(publication).toFloat(),
          progress = KoreaderPosition.encode(position.spineIndex),
          // Kavita's controller ignores the inbound timestamp and stamps its own UTC clock
          // (KoreaderProgressUpdateDto sets Timestamp = DateTime.UtcNow). We still send the
          // epoch seconds the kosync protocol expects.
          timestamp = timestampMillis / 1000,
        )
      runCatchingNetwork { api.putKoreaderProgress(creds.baseUrl, creds.apiKey, payload) }
    }

  override suspend fun pull(file: File, publication: Publication): Result<RemoteProgress?> =
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
          Log.w(TAG, "Remote progress had no decodable spine index; skipping")
          return@runCatchingNetwork null
        }
        RemoteProgress(
          position = ReaderPosition(spineIndex = spineIndex, pageIndex = 0, progressInSpine = 0f),
          percentage = remote.percentage.toDouble(),
          // KOReader's kosync protocol uses epoch seconds; we expose millis everywhere else.
          timestampMillis = remote.timestamp * 1000L,
          deviceName = remote.device.takeIf { it.isNotBlank() },
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
}

// Sentinel exceptions so callers can distinguish "skip, no setup" from "retry later."
object NoCredentialsException : RuntimeException("No Kavita credentials configured")

object MissingFileException : RuntimeException("Book file is missing")

object UnresolvableLocatorException : RuntimeException("Locator doesn't map to a spine item")
