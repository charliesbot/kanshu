package com.charliesbot.kanshu.core.provider

import com.charliesbot.kanshu.core.reader.ReaderHighlightColor
import com.charliesbot.kanshu.core.reader.SourceElementPath
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import java.io.File
import org.readium.r2.shared.publication.Publication

typealias ProviderMetadata = Map<String, String>

/**
 * Provider-specific catalog, acquisition, and synchronization operations behind a shared contract.
 */
interface Provider {
  val descriptor: ProviderDescriptor

  suspend fun fetchCatalog(): ProviderResult<List<ProviderBook>>

  suspend fun resolveCover(book: ProviderBookKey, revisionToken: String?): ProviderCover?

  suspend fun acquire(
    book: ProviderBookKey,
    target: File,
    onProgress: (downloaded: Long, total: Long?) -> Unit,
  ): ProviderResult<AcquiredBook>

  /**
   * Acquires a book with opaque catalog metadata that the provider may enrich in [AcquiredBook].
   * The default delegates to acquisition without metadata for providers that do not need it.
   */
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

  /**
   * Returns a complete remote snapshot, including IDs whose anchors cannot be translated. A failed
   * or incomplete fetch must return failure rather than a partial success: missing IDs are used to
   * remove linked local highlights. Called only for providers supporting highlight sync.
   */
  suspend fun pullHighlights(
    context: ProviderHighlightContext
  ): ProviderResult<ProviderHighlightSnapshot> =
    ProviderResult.Success(ProviderHighlightSnapshot(emptySet(), emptyList()))

  /**
   * Pushes one local mutation and returns its acknowledgement, including the remote ID on creation.
   * Failures leave the local change pending. Called only for providers supporting highlight sync.
   */
  suspend fun pushHighlight(
    context: ProviderHighlightContext,
    change: HighlightChange,
  ): ProviderResult<HighlightPushAck> = ProviderResult.Success(HighlightPushAck())
}

/** Acquisition result containing the downloaded size and any provider-enriched metadata. */
data class AcquiredBook(
  val byteSize: Long,
  val providerMetadata: ProviderMetadata = emptyMap(),
)

/** An opened book and its opaque provider metadata, shared by progress and highlight adapters. */
data class ProviderBookContext(
  val book: ProviderBookKey,
  val file: File,
  val publication: Publication,
  val providerMetadata: ProviderMetadata = emptyMap(),
)

/**
 * Book context plus lazy access to source maps for zero-based EPUB spine indexes.
 * [sourceMapForSpine] may load or parse a section and returns null when its source map is
 * unavailable.
 */
data class ProviderHighlightContext(
  val book: ProviderBookContext,
  val sourceMapForSpine: suspend (Int) -> ProviderSourceMap?,
)

/**
 * Read-only source structure and text lookup for translating provider anchors outside rendering.
 */
interface ProviderSourceMap {
  /** Returns element metadata at a body-relative path, or null when that element is absent. */
  fun inspect(path: SourceElementPath): ProviderSourceElement?

  /**
   * Resolves a zero-based element-child index, excluding text nodes and comments; null if absent.
   */
  fun resolveChild(parent: SourceElementPath, elementChildIndex: Int): SourceElementPath?

  /** Resolves an XHTML element ID to its body-relative path, or null if absent. */
  fun resolveElementId(id: String): SourceElementPath?

  /**
   * Finds selected text within the range covered by the start and end elements, including
   * descendants. Matching collapses whitespace and accounts for block boundaries. Returns an
   * inclusive range of original flattened-text offsets (use last + 1 as the exclusive end), or null
   * when the anchors or text cannot be resolved. Repeated text resolves to the first occurrence;
   * element anchors cannot distinguish occurrences.
   */
  fun findFirstLiteralMatch(
    startElementPath: SourceElementPath,
    endElementPath: SourceElementPath,
    selectedText: String,
  ): IntRange?
}

/**
 * Element metadata used to translate between source paths and provider-specific anchors.
 * [sameTagSiblingIndex] is zero-based among siblings with the same tag; [childPaths] are in DOM
 * order. [textRange] is an inclusive flattened-text range, or null when the element has no mapped
 * text.
 */
data class ProviderSourceElement(
  val path: SourceElementPath,
  val tagName: String,
  val id: String?,
  val sameTagSiblingIndex: Int,
  val childPaths: List<SourceElementPath>,
  val textRange: IntRange?,
)

/**
 * Remote reading progress; a null [position] means the anchor could not be decoded. [percentage]
 * still allows comparison with local progress even when the precise position is unknown.
 */
data class RemoteProgress(
  val position: ReaderPosition?,
  val percentage: Double,
  val timestampMillis: Long,
)

/**
 * A translated remote highlight within one zero-based spine section. Character offsets use an
 * exclusive end; source paths identify elements, not exact text positions. [createdAt] and
 * [updatedAt] are epoch milliseconds supplied by the provider adapter.
 */
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

/**
 * Complete remote inventory used for local reconciliation. [seenRemoteIds] includes untranslatable
 * annotations; [highlights] contains only translated ones. An ID absent from [highlights] but
 * present in [seenRemoteIds] must not cause local deletion.
 */
data class ProviderHighlightSnapshot(
  val seenRemoteIds: Set<String>,
  val highlights: List<ProviderHighlight>,
)

/**
 * A pending local mutation captured before network work begins. [expectedUpdatedAt] identifies the
 * local version being acknowledged, so newer edits stay pending.
 */
sealed interface HighlightChange {
  val localId: String
  val remoteId: String?
  val expectedUpdatedAt: Long

  /** Creates a remote highlight when [remoteId] is null; otherwise updates the linked highlight. */
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

  /**
   * Deletes a linked remote highlight; a null [remoteId] means there is no remote row to delete.
   */
  data class Delete(
    override val localId: String,
    override val remoteId: String?,
    override val expectedUpdatedAt: Long,
  ) : HighlightChange
}

/** Successful mutation acknowledgement; creates must return the assigned [remoteId]. */
data class HighlightPushAck(val remoteId: String? = null)
