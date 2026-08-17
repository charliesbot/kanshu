package com.charliesbot.kanshu.core.provider.kavita

import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaException
import com.charliesbot.kanshu.core.provider.AcquiredBook
import com.charliesbot.kanshu.core.provider.Provider
import com.charliesbot.kanshu.core.provider.ProviderBook
import com.charliesbot.kanshu.core.provider.ProviderBookKey
import com.charliesbot.kanshu.core.provider.ProviderCapabilities
import com.charliesbot.kanshu.core.provider.ProviderCover
import com.charliesbot.kanshu.core.provider.ProviderDescriptor
import com.charliesbot.kanshu.core.provider.ProviderError
import com.charliesbot.kanshu.core.provider.ProviderInstanceId
import com.charliesbot.kanshu.core.provider.ProviderResult
import com.charliesbot.kanshu.core.provider.ProviderType
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class KavitaProvider(
  private val credentials: CredentialsRepository,
  private val api: KavitaApi,
) : Provider {
  override val descriptor =
    ProviderDescriptor(
      id = ID,
      type = ProviderType.KAVITA,
      displayName = "Kavita",
      enabled = true,
      capabilities = ProviderCapabilities(progressSync = true, highlightSync = false),
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
          )
        }
    }
  }

  override suspend fun acquire(
    book: ProviderBookKey,
    target: File,
    onProgress: (downloaded: Long, total: Long?) -> Unit,
  ): ProviderResult<AcquiredBook> {
    require(book.providerId == ID) {
      "Kavita cannot acquire a book owned by ${book.providerId.value}"
    }
    val seriesId =
      book.providerItemId.toIntOrNull()
        ?: return ProviderResult.Failure(ProviderError.MalformedResponse)
    val credentials =
      credentials.credentials.first() ?: return ProviderResult.Failure(ProviderError.NoCredentials)
    return try {
      val chapterId =
        api
          .listVolumes(credentials.baseUrl, credentials.apiKey, seriesId)
          .sortedBy { it.id }
          .asSequence()
          .flatMap { it.chapters.asSequence() }
          .firstOrNull()
          ?.id ?: return ProviderResult.Failure(ProviderError.MalformedResponse)
      api.downloadChapter(
        baseUrl = credentials.baseUrl,
        apiKey = credentials.apiKey,
        chapterId = chapterId,
        target = target,
        onProgress = onProgress,
      )
      ProviderResult.Success(AcquiredBook(target.length()))
    } catch (e: CancellationException) {
      throw e
    } catch (e: KavitaException) {
      ProviderResult.Failure(e.toProviderError())
    } catch (e: Exception) {
      ProviderResult.Failure(ProviderError.Unknown(e.message))
    }
  }

  override suspend fun resolveCover(
    book: ProviderBookKey,
    revisionToken: String?,
  ): ProviderCover? {
    require(book.providerId == ID) {
      "Kavita cannot resolve a cover owned by ${book.providerId.value}"
    }
    if (revisionToken == null) return null
    val seriesId = book.providerItemId.toIntOrNull() ?: return null
    val credentials = credentials.credentials.first() ?: return null
    return ProviderCover.RemoteUrl(buildCoverUrl(credentials.baseUrl, seriesId, credentials.apiKey))
  }

  companion object {
    val ID = ProviderInstanceId("kavita")
    private const val DEFAULT_PAGE_SIZE = 100
    private const val EPUB_MEDIA_TYPE = "application/epub+zip"
  }
}

private suspend fun <T> providerCall(block: suspend () -> T): ProviderResult<T> =
  try {
    ProviderResult.Success(block())
  } catch (e: CancellationException) {
    throw e
  } catch (e: KavitaException) {
    ProviderResult.Failure(e.toProviderError())
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
  return "${baseUrl.trimEnd('/')}/api/Image/series-cover?seriesId=$seriesId&apiKey=$encodedKey"
}
