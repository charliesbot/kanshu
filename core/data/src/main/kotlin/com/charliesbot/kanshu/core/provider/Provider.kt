package com.charliesbot.kanshu.core.provider

import com.charliesbot.kanshu.core.reader.ReaderHighlightColor
import com.charliesbot.kanshu.core.reader.SourceElementPath
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import java.io.File
import org.readium.r2.shared.publication.Publication

typealias ProviderMetadata = Map<String, String>

interface Provider {
  val descriptor: ProviderDescriptor

  suspend fun fetchCatalog(): ProviderResult<List<ProviderBook>>

  suspend fun resolveCover(book: ProviderBookKey, revisionToken: String?): ProviderCover?

  suspend fun acquire(
    book: ProviderBookKey,
    target: File,
    onProgress: (downloaded: Long, total: Long?) -> Unit,
  ): ProviderResult<AcquiredBook>

  suspend fun acquire(
    book: ProviderBookKey,
    metadata: ProviderMetadata,
    target: File,
    onProgress: (downloaded: Long, total: Long?) -> Unit,
  ): ProviderResult<AcquiredBook> = acquire(book, target, onProgress)

  suspend fun pullProgress(context: ProviderBookContext): ProviderResult<RemoteProgress?> =
    ProviderResult.Success(null)

  suspend fun pushProgress(
    context: ProviderBookContext,
    position: ReaderPosition,
  ): ProviderResult<Unit> = ProviderResult.Success(Unit)

  suspend fun pullHighlights(
    context: ProviderHighlightContext
  ): ProviderResult<ProviderHighlightSnapshot> =
    ProviderResult.Success(ProviderHighlightSnapshot(emptySet(), emptyList()))

  suspend fun pushHighlight(
    context: ProviderHighlightContext,
    change: HighlightChange,
  ): ProviderResult<HighlightPushAck> = ProviderResult.Success(HighlightPushAck())
}

data class AcquiredBook(
  val byteSize: Long,
  val providerMetadata: ProviderMetadata = emptyMap(),
)

data class ProviderBookContext(
  val book: ProviderBookKey,
  val file: File,
  val publication: Publication,
  val providerMetadata: ProviderMetadata = emptyMap(),
)

data class ProviderHighlightContext(
  val book: ProviderBookContext,
  val sourceMapForSpine: suspend (Int) -> ProviderSourceMap?,
)

interface ProviderSourceMap {
  fun inspect(path: SourceElementPath): ProviderSourceElement?

  fun resolveChild(parent: SourceElementPath, elementChildIndex: Int): SourceElementPath?

  fun resolveElementId(id: String): SourceElementPath?

  fun findFirstLiteralMatch(
    startElementPath: SourceElementPath,
    endElementPath: SourceElementPath,
    selectedText: String,
  ): IntRange?
}

data class ProviderSourceElement(
  val path: SourceElementPath,
  val tagName: String,
  val id: String?,
  val sameTagSiblingIndex: Int,
  val childPaths: List<SourceElementPath>,
  val textRange: IntRange?,
)

data class RemoteProgress(
  val position: ReaderPosition?,
  val percentage: Double,
  val timestampMillis: Long,
)

data class ProviderHighlight(
  val remoteId: String,
  val spineIndex: Int,
  val startCharOffset: Int,
  val endCharOffset: Int,
  val selectedText: String,
  val startElementPath: SourceElementPath,
  val endElementPath: SourceElementPath,
  val color: ReaderHighlightColor,
  val createdAt: Long,
  val updatedAt: Long,
)

data class ProviderHighlightSnapshot(
  val seenRemoteIds: Set<String>,
  val highlights: List<ProviderHighlight>,
)

sealed interface HighlightChange {
  val localId: String
  val remoteId: String?
  val expectedUpdatedAt: Long

  data class Upsert(
    override val localId: String,
    override val remoteId: String?,
    override val expectedUpdatedAt: Long,
    val spineIndex: Int,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val selectedText: String,
    val startElementPath: SourceElementPath,
    val endElementPath: SourceElementPath,
    val color: ReaderHighlightColor,
    val createdAt: Long,
  ) : HighlightChange

  data class Delete(
    override val localId: String,
    override val remoteId: String?,
    override val expectedUpdatedAt: Long,
  ) : HighlightChange
}

data class HighlightPushAck(val remoteId: String? = null)
