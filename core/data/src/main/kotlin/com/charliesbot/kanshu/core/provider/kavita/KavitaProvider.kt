package com.charliesbot.kanshu.core.provider.kavita

import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaException
import com.charliesbot.kanshu.core.kavita.dto.AnnotationDto
import com.charliesbot.kanshu.core.provider.AcquiredBook
import com.charliesbot.kanshu.core.provider.HighlightChange
import com.charliesbot.kanshu.core.provider.HighlightPushAck
import com.charliesbot.kanshu.core.provider.Provider
import com.charliesbot.kanshu.core.provider.ProviderBook
import com.charliesbot.kanshu.core.provider.ProviderBookContext
import com.charliesbot.kanshu.core.provider.ProviderBookKey
import com.charliesbot.kanshu.core.provider.ProviderCapabilities
import com.charliesbot.kanshu.core.provider.ProviderCover
import com.charliesbot.kanshu.core.provider.ProviderDescriptor
import com.charliesbot.kanshu.core.provider.ProviderError
import com.charliesbot.kanshu.core.provider.ProviderHighlight
import com.charliesbot.kanshu.core.provider.ProviderHighlightContext
import com.charliesbot.kanshu.core.provider.ProviderHighlightSnapshot
import com.charliesbot.kanshu.core.provider.ProviderInstanceId
import com.charliesbot.kanshu.core.provider.ProviderMetadata
import com.charliesbot.kanshu.core.provider.ProviderResult
import com.charliesbot.kanshu.core.provider.ProviderSourceMap
import com.charliesbot.kanshu.core.provider.ProviderType
import com.charliesbot.kanshu.core.provider.RemoteProgress
import com.charliesbot.kanshu.core.reader.ReaderHighlightColor
import com.charliesbot.kanshu.core.reader.SourceElementPath
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class KavitaProvider(
  private val credentials: CredentialsRepository,
  private val api: KavitaApi,
) : Provider {
  private val progress = KavitaProgressAdapter(api, credentials)
  override val descriptor =
    ProviderDescriptor(
      id = ID,
      type = ProviderType.KAVITA,
      displayName = "Kavita",
      enabled = true,
      capabilities = ProviderCapabilities(progressSync = true, highlightSync = true),
    )

  override suspend fun fetchCatalog(): ProviderResult<List<ProviderBook>> {
    val credentials =
      credentials.credentials.first() ?: return ProviderResult.Failure(ProviderError.NoCredentials)
    return providerCall {
      api
        .listSeries(
          baseUrl = credentials.baseUrl,
          apiKey = credentials.apiKey,
          pageNumber = 1,
          pageSize = DEFAULT_PAGE_SIZE,
        )
        .map { series ->
          ProviderBook(
            key = ProviderBookKey(ID, series.id.toString()),
            title = series.name,
            cover = series.coverImage?.let { ProviderCover.Available },
            mediaType = EPUB_MEDIA_TYPE,
            revisionToken = series.coverImage,
            providerMetadata =
              mapOf(
                SERIES_ID to series.id.toString(),
                LIBRARY_ID to series.libraryId.toString(),
              ),
          )
        }
    }
  }

  override suspend fun acquire(
    book: ProviderBookKey,
    target: File,
    onProgress: (downloaded: Long, total: Long?) -> Unit,
  ): ProviderResult<AcquiredBook> =
    acquire(book, mapOf(SERIES_ID to book.providerItemId), target, onProgress)

  override suspend fun acquire(
    book: ProviderBookKey,
    metadata: ProviderMetadata,
    target: File,
    onProgress: (downloaded: Long, total: Long?) -> Unit,
  ): ProviderResult<AcquiredBook> {
    require(book.providerId == ID) {
      "Kavita cannot acquire a book owned by " + book.providerId.value
    }
    val seriesId =
      metadata[SERIES_ID]?.toIntOrNull()
        ?: book.providerItemId.toIntOrNull()
        ?: return ProviderResult.Failure(ProviderError.MalformedResponse)
    val credentials =
      credentials.credentials.first() ?: return ProviderResult.Failure(ProviderError.NoCredentials)
    return try {
      val volume =
        api.listVolumes(credentials.baseUrl, credentials.apiKey, seriesId).minByOrNull { it.id }
          ?: return ProviderResult.Failure(ProviderError.MalformedResponse)
      val chapter =
        volume.chapters.minByOrNull { it.id }
          ?: return ProviderResult.Failure(ProviderError.MalformedResponse)
      api.downloadChapter(
        baseUrl = credentials.baseUrl,
        apiKey = credentials.apiKey,
        chapterId = chapter.id,
        target = target,
        onProgress = onProgress,
      )
      ProviderResult.Success(
        AcquiredBook(
          byteSize = target.length(),
          providerMetadata =
            metadata +
              mapOf(
                SERIES_ID to seriesId.toString(),
                VOLUME_ID to volume.id.toString(),
                CHAPTER_ID to chapter.id.toString(),
              ),
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: KavitaException) {
      ProviderResult.Failure(e.toProviderError())
    } catch (e: Exception) {
      ProviderResult.Failure(ProviderError.Unknown(e.message))
    }
  }

  override suspend fun pullHighlights(
    context: ProviderHighlightContext
  ): ProviderResult<ProviderHighlightSnapshot> {
    require(context.book.book.providerId == ID)
    val metadata = context.book.providerMetadata
    val seriesId =
      metadata[SERIES_ID]?.toIntOrNull()
        ?: return ProviderResult.Failure(ProviderError.MalformedResponse)
    val credentials =
      credentials.credentials.first() ?: return ProviderResult.Failure(ProviderError.NoCredentials)
    return providerCall {
      val annotations = api.listAnnotations(credentials.baseUrl, credentials.apiKey, seriesId)
      val seenIds = annotations.mapTo(mutableSetOf()) { it.id.toString() }
      val highlights = annotations.mapNotNull { annotation ->
        val sourceMap = context.sourceMapForSpine(annotation.pageNumber) ?: return@mapNotNull null
        val startPath = resolveKavitaXPath(annotation.xPath, sourceMap) ?: return@mapNotNull null
        val endPath =
          resolveKavitaXPath(annotation.endingXPath, sourceMap) ?: return@mapNotNull null
        val range =
          sourceMap.findFirstLiteralMatch(startPath, endPath, annotation.selectedText)
            ?: return@mapNotNull null
        val color = colorForSlot(annotation.selectedSlotIndex) ?: return@mapNotNull null
        ProviderHighlight(
          remoteId = annotation.id.toString(),
          spineIndex = annotation.pageNumber,
          startCharOffset = range.first,
          endCharOffset = range.last + 1,
          selectedText = annotation.selectedText,
          startElementPath = startPath,
          endElementPath = endPath,
          color = color,
          createdAt = annotation.createdUtc.toEpochMillis(),
          updatedAt = (annotation.lastModifiedUtc ?: annotation.createdUtc).toEpochMillis(),
        )
      }
      ProviderHighlightSnapshot(seenRemoteIds = seenIds, highlights = highlights)
    }
  }

  override suspend fun pushHighlight(
    context: ProviderHighlightContext,
    change: HighlightChange,
  ): ProviderResult<HighlightPushAck> {
    require(context.book.book.providerId == ID)
    val credentials =
      credentials.credentials.first() ?: return ProviderResult.Failure(ProviderError.NoCredentials)
    return providerCall {
      when (change) {
        is HighlightChange.Delete -> {
          val remoteId =
            change.remoteId?.toIntOrNull()
              ?: throw MalformedHighlightException("Missing Kavita annotation ID")
          api.deleteAnnotation(credentials.baseUrl, credentials.apiKey, remoteId)
          HighlightPushAck(remoteId = change.remoteId)
        }
        is HighlightChange.Upsert -> {
          val metadata = context.book.providerMetadata
          val sourceMap =
            context.sourceMapForSpine(change.spineIndex)
              ?: throw MalformedHighlightException("Missing source map")
          val annotation =
            AnnotationDto(
              id = change.remoteId?.toIntOrNull() ?: 0,
              xPath =
                toKavitaXPath(change.startElementPath, sourceMap)
                  ?: throw MalformedHighlightException("Invalid start path"),
              endingXPath =
                toKavitaXPath(change.endElementPath, sourceMap)
                  ?: throw MalformedHighlightException("Invalid end path"),
              selectedText = change.selectedText,
              selectedSlotIndex = slotForColor(change.color),
              pageNumber = change.spineIndex,
              chapterId = metadata.requiredInt(CHAPTER_ID),
              volumeId = metadata.requiredInt(VOLUME_ID),
              seriesId = metadata.requiredInt(SERIES_ID),
              libraryId = metadata.requiredInt(LIBRARY_ID),
              createdUtc = Instant.ofEpochMilli(change.createdAt).toString(),
              lastModifiedUtc = Instant.ofEpochMilli(change.expectedUpdatedAt).toString(),
            )
          val saved =
            if (change.remoteId == null) {
              api.createAnnotation(credentials.baseUrl, credentials.apiKey, annotation)
            } else {
              api.updateAnnotation(credentials.baseUrl, credentials.apiKey, annotation)
            }
          HighlightPushAck(remoteId = saved.id.toString())
        }
      }
    }
  }

  override suspend fun pullProgress(context: ProviderBookContext): ProviderResult<RemoteProgress?> {
    require(context.book.providerId == ID)
    return progress.pull(context.file, context.publication).toProviderResult()
  }

  override suspend fun pushProgress(
    context: ProviderBookContext,
    position: ReaderPosition,
  ): ProviderResult<Unit> {
    require(context.book.providerId == ID)
    return progress
      .push(context.file, position, context.publication, System.currentTimeMillis())
      .toProviderResult()
  }

  override suspend fun resolveCover(
    book: ProviderBookKey,
    revisionToken: String?,
  ): ProviderCover? {
    require(book.providerId == ID) {
      "Kavita cannot resolve a cover owned by " + book.providerId.value
    }
    if (revisionToken == null) return null
    val seriesId = book.providerItemId.toIntOrNull() ?: return null
    val credentials = credentials.credentials.first() ?: return null
    return ProviderCover.RemoteUrl(buildCoverUrl(credentials.baseUrl, seriesId, credentials.apiKey))
  }

  companion object {
    val ID = ProviderInstanceId("kavita")
    internal const val SERIES_ID = "seriesId"
    internal const val LIBRARY_ID = "libraryId"
    internal const val VOLUME_ID = "volumeId"
    internal const val CHAPTER_ID = "chapterId"
    private const val DEFAULT_PAGE_SIZE = 100
    private const val EPUB_MEDIA_TYPE = "application/epub+zip"
  }
}

internal fun toKavitaXPath(
  path: SourceElementPath,
  sourceMap: ProviderSourceMap,
): String? {
  var current = SourceElementPath.Root
  val segments = mutableListOf<String>()
  path.childIndexes.forEach { childIndex ->
    current = sourceMap.resolveChild(current, childIndex) ?: return null
    val element = sourceMap.inspect(current) ?: return null
    segments += element.tagName.lowercase() + "[" + (element.sameTagSiblingIndex + 1) + "]"
  }
  return "//body" +
    segments.joinToString(separator = "/", prefix = if (segments.isEmpty()) "" else "/")
}

internal fun resolveKavitaXPath(
  xpath: String,
  sourceMap: ProviderSourceMap,
): SourceElementPath? {
  val trimmed = xpath.trim()
  var current = SourceElementPath.Root
  var remaining = trimmed
  if (trimmed.startsWith("id(")) {
    val close = trimmed.indexOf(')')
    if (close < 4) return null
    val id = trimmed.substring(3, close).trim().trim('"', '\'')
    current = sourceMap.resolveElementId(id) ?: return null
    remaining = trimmed.substring(close + 1)
  }

  val segments = remaining.split('/').filter { it.isNotBlank() }.toMutableList()
  while (segments.firstOrNull()?.substringBefore('[')?.lowercase() in setOf("html", "body")) {
    segments.removeAt(0)
  }
  for (segment in segments) {
    val tag = segment.substringBefore('[').lowercase()
    if (tag.isBlank() || !tag.first().isLetter()) return null
    val indexText = segment.substringAfter('[', "").substringBefore(']', "")
    if ('[' in segment && (indexText.isBlank() || !segment.endsWith(']'))) return null
    val sibling = (indexText.toIntOrNull() ?: 1) - 1
    if (sibling < 0) return null
    val parent = sourceMap.inspect(current) ?: return null
    current =
      parent.childPaths
        .mapNotNull(sourceMap::inspect)
        .firstOrNull {
          it.tagName.equals(tag, ignoreCase = true) && it.sameTagSiblingIndex == sibling
        }
        ?.path ?: return null
  }
  return current
}

internal fun slotForColor(color: ReaderHighlightColor): Int =
  when (color) {
    ReaderHighlightColor.Aqua -> 0
    ReaderHighlightColor.Green -> 1
    ReaderHighlightColor.Yellow -> 2
    ReaderHighlightColor.Orange -> 3
    ReaderHighlightColor.Pink -> 4
  }

internal fun colorForSlot(slot: Int): ReaderHighlightColor? =
  when (slot) {
    0 -> ReaderHighlightColor.Aqua
    1 -> ReaderHighlightColor.Green
    2 -> ReaderHighlightColor.Yellow
    3 -> ReaderHighlightColor.Orange
    4 -> ReaderHighlightColor.Pink
    else -> null
  }

private fun ProviderMetadata.requiredInt(key: String): Int =
  get(key)?.toIntOrNull() ?: throw MalformedHighlightException("Missing " + key)

private fun String?.toEpochMillis(): Long =
  this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

private class MalformedHighlightException(message: String) : Exception(message)

private fun <T> Result<T>.toProviderResult(): ProviderResult<T> =
  fold(
    onSuccess = ProviderResult<T>::Success,
    onFailure = {
      ProviderResult.Failure(
        when (it) {
          is KavitaException -> it.toProviderError()
          NoCredentialsException -> ProviderError.NoCredentials
          else -> ProviderError.Unknown(it.message)
        }
      )
    },
  )

private suspend fun <T> providerCall(block: suspend () -> T): ProviderResult<T> =
  try {
    ProviderResult.Success(block())
  } catch (e: CancellationException) {
    throw e
  } catch (e: KavitaException) {
    ProviderResult.Failure(e.toProviderError())
  } catch (e: MalformedHighlightException) {
    ProviderResult.Failure(ProviderError.MalformedResponse)
  } catch (e: Exception) {
    ProviderResult.Failure(ProviderError.Unknown(e.message))
  }

private fun KavitaException.toProviderError(): ProviderError =
  when (this) {
    KavitaException.Unauthorized -> ProviderError.Unauthorized
    KavitaException.NetworkError -> ProviderError.Network
    KavitaException.UnexpectedResponse -> ProviderError.MalformedResponse
    is KavitaException.Unknown -> ProviderError.Unknown(message)
  }

private fun buildCoverUrl(baseUrl: String, seriesId: Int, apiKey: String): String {
  val encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())
  return baseUrl.trimEnd('/') +
    "/api/Image/series-cover?seriesId=" +
    seriesId +
    "&apiKey=" +
    encodedKey
}
