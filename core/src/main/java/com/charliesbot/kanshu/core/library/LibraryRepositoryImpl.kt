package com.charliesbot.kanshu.core.library

import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.connection.KavitaCredentials
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaException
import com.charliesbot.kanshu.core.kavita.dto.SeriesDto
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val DEFAULT_PAGE_SIZE = 100

class LibraryRepositoryImpl(
  private val credentialsRepository: CredentialsRepository,
  private val api: KavitaApi,
) : LibraryRepository {
  override suspend fun loadLibrary(): LibraryResult {
    val credentials =
      credentialsRepository.credentials.first() ?: return LibraryResult.NoCredentials
    return try {
      val series =
        api.listSeries(
          baseUrl = credentials.baseUrl,
          apiKey = credentials.apiKey,
          pageNumber = 1,
          pageSize = DEFAULT_PAGE_SIZE,
        )
      val items = series.map { it.toLibraryItem(credentials) }
      if (items.isEmpty()) LibraryResult.Empty else LibraryResult.Success(items)
    } catch (e: CancellationException) {
      throw e
    } catch (e: KavitaException) {
      e.toLibraryError()
    }
  }
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
