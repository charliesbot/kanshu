package com.charliesbot.kanshu.core.provider

import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import java.io.File
import org.readium.r2.shared.publication.Publication

interface Provider {
  val descriptor: ProviderDescriptor

  suspend fun fetchCatalog(): ProviderResult<List<ProviderBook>>

  suspend fun resolveCover(book: ProviderBookKey, revisionToken: String?): ProviderCover?

  suspend fun acquire(
    book: ProviderBookKey,
    target: File,
    onProgress: (downloaded: Long, total: Long?) -> Unit,
  ): ProviderResult<AcquiredBook>

  suspend fun pullProgress(context: ProviderBookContext): ProviderResult<RemoteProgress?> =
    ProviderResult.Success(null)

  suspend fun pushProgress(
    context: ProviderBookContext,
    position: ReaderPosition,
  ): ProviderResult<Unit> = ProviderResult.Success(Unit)
}

data class AcquiredBook(val byteSize: Long)

data class ProviderBookContext(
  val book: ProviderBookKey,
  val file: File,
  val publication: Publication,
)

/**
 * @property position Null when the remote's position couldn't be decoded into our spine model —
 *   another kosync client's XPointer, or the numeric-only form Kavita sends for PDFs. The record is
 *   still reported rather than dropped, because [percentage] alone is enough to tell that the
 *   remote is further along, and dropping it would silently disarm the pre-push check.
 */
data class RemoteProgress(
  val position: ReaderPosition?,
  val percentage: Double,
  val timestampMillis: Long,
)
