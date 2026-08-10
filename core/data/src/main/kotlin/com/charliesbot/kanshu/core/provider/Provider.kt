package com.charliesbot.kanshu.core.provider

import com.charliesbot.kanshu.core.reader.ReaderHighlightColor
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

  suspend fun pullHighlights(
    context: ProviderBookContext
  ): ProviderResult<List<ProviderHighlight>> = ProviderResult.Success(emptyList())

  suspend fun pushHighlights(
    context: ProviderBookContext,
    changes: List<HighlightChange>,
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

/** Provider-neutral highlight representation used only at the remote adapter boundary. */
data class ProviderHighlight(
  val localId: String?,
  val remoteId: String?,
  val spineIndex: Int,
  val startCharOffset: Int,
  val endCharOffset: Int,
  val selectedText: String,
  val color: ReaderHighlightColor,
  val createdAt: Long,
  val updatedAt: Long,
  val remoteRevision: String?,
)

sealed interface HighlightChange {
  data class Upsert(val highlight: ProviderHighlight) : HighlightChange

  data class Delete(
    val localId: String,
    val remoteId: String?,
    val remoteRevision: String?,
  ) : HighlightChange
}
