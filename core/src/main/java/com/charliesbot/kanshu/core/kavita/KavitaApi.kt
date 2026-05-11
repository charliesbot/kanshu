package com.charliesbot.kanshu.core.kavita

import android.util.Log
import com.charliesbot.kanshu.core.kavita.dto.SeriesDto
import com.charliesbot.kanshu.core.kavita.dto.ServerInfoSlim
import com.charliesbot.kanshu.core.kavita.dto.VolumeDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

private const val TAG = "KavitaApi"
private const val DOWNLOAD_BUFFER_SIZE = 16 * 1024
private val EpubContentType = ContentType("application", "epub+zip")

interface KavitaApi {
  suspend fun serverInfoSlim(baseUrl: String, apiKey: String): ServerInfoSlim

  suspend fun listSeries(
    baseUrl: String,
    apiKey: String,
    pageNumber: Int,
    pageSize: Int,
  ): List<SeriesDto>

  suspend fun listVolumes(baseUrl: String, apiKey: String, seriesId: Int): List<VolumeDto>

  // Streams the chapter file to [target]. Progress is reported as (bytesSoFar, totalBytes); the
  // total is null when the server didn't send a Content-Length. Throws KavitaException on
  // failure; the caller owns cleanup of a partial [target].
  suspend fun downloadChapter(
    baseUrl: String,
    apiKey: String,
    chapterId: Int,
    target: File,
    onProgress: (bytesSoFar: Long, totalBytes: Long?) -> Unit,
  )
}

class KavitaApiImpl(private val client: HttpClient) : KavitaApi {
  override suspend fun serverInfoSlim(baseUrl: String, apiKey: String): ServerInfoSlim {
    val response =
      executeRequest("${baseUrl.trimEnd('/')}/api/Server/server-info-slim", HttpMethod.Get) {
        header("x-api-key", apiKey)
      }
    return decodeJsonBody(response)
  }

  override suspend fun listSeries(
    baseUrl: String,
    apiKey: String,
    pageNumber: Int,
    pageSize: Int,
  ): List<SeriesDto> {
    val response =
      executeRequest("${baseUrl.trimEnd('/')}/api/Series/all-v2", HttpMethod.Post) {
        header("x-api-key", apiKey)
        parameter("PageNumber", pageNumber)
        parameter("PageSize", pageSize)
        contentType(ContentType.Application.Json)
        setBody("{}")
      }
    return decodeJsonBody(response)
  }

  override suspend fun listVolumes(
    baseUrl: String,
    apiKey: String,
    seriesId: Int,
  ): List<VolumeDto> {
    val response =
      executeRequest("${baseUrl.trimEnd('/')}/api/Series/volumes", HttpMethod.Get) {
        header("x-api-key", apiKey)
        parameter("seriesId", seriesId)
      }
    return decodeJsonBody(response)
  }

  override suspend fun downloadChapter(
    baseUrl: String,
    apiKey: String,
    chapterId: Int,
    target: File,
    onProgress: (bytesSoFar: Long, totalBytes: Long?) -> Unit,
  ) {
    val url = "${baseUrl.trimEnd('/')}/api/Download/chapter"
    mapKavitaErrors(url) {
      val response =
        client.request(url) {
          method = HttpMethod.Get
          header("x-api-key", apiKey)
          parameter("chapterId", chapterId)
        }
      when (response.status) {
        HttpStatusCode.Unauthorized,
        HttpStatusCode.Forbidden -> throw KavitaException.Unauthorized
        HttpStatusCode.OK -> Unit
        else -> throw KavitaException.Unknown("HTTP ${response.status.value}")
      }
      // Kavita returns application/zip for multi-file chapters. Phase 0 only supports the
      // single-file EPUB case (see docs/KAVITA_API.md). Reject anything else so we never write
      // a bogus payload to disk. Strip parameters first — a future "; charset=…" appended by
      // the server must not cause a false reject.
      val ct = response.contentType()?.withoutParameters()
      if (ct == null || !ct.match(EpubContentType)) {
        throw KavitaException.UnexpectedResponse
      }
      streamToFile(response.bodyAsChannel(), target, response.contentLength(), onProgress)
    }
  }

  // Bridges the Ktor ByteReadChannel through a JVM InputStream so we can write to disk with
  // standard buffered IO and avoid Ktor's evolving channel API. The bridge does its suspending
  // reads via runBlocking under the hood; the caller must be on a dispatcher that tolerates
  // blocking (Dispatchers.IO in our case). ensureActive() between chunks lets a cancellation
  // (e.g., the download scope shutting down) abort the loop instead of blocking inside read().
  private suspend fun streamToFile(
    channel: ByteReadChannel,
    target: File,
    totalBytes: Long?,
    onProgress: (Long, Long?) -> Unit,
  ) {
    var bytesSoFar = 0L
    target.outputStream().use { output ->
      channel.toInputStream().use { input ->
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        while (true) {
          coroutineContext.ensureActive()
          val read = input.read(buffer)
          if (read < 0) break
          if (read == 0) continue
          output.write(buffer, 0, read)
          bytesSoFar += read
          onProgress(bytesSoFar, totalBytes)
        }
      }
    }
  }

  private suspend fun executeRequest(
    url: String,
    httpMethod: HttpMethod,
    block: HttpRequestBuilder.() -> Unit,
  ): HttpResponse =
    mapKavitaErrors(url) {
      client.request(url) {
        method = httpMethod
        block()
      }
    }

  private suspend inline fun <T> mapKavitaErrors(url: String, block: () -> T): T =
    try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (e: KavitaException) {
      throw e
    } catch (e: HttpRequestTimeoutException) {
      Log.w(TAG, "Request timeout for $url", e)
      throw KavitaException.NetworkError
    } catch (e: ConnectTimeoutException) {
      Log.w(TAG, "Connect timeout for $url", e)
      throw KavitaException.NetworkError
    } catch (e: SocketTimeoutException) {
      Log.w(TAG, "Socket timeout for $url", e)
      throw KavitaException.NetworkError
    } catch (e: IOException) {
      Log.w(TAG, "Network failure for $url", e)
      throw KavitaException.NetworkError
    } catch (e: Exception) {
      Log.w(TAG, "Unexpected failure for $url", e)
      throw KavitaException.Unknown(e.message ?: e.javaClass.simpleName)
    }

  private suspend inline fun <reified T> decodeJsonBody(response: HttpResponse): T {
    when (response.status) {
      HttpStatusCode.Unauthorized,
      HttpStatusCode.Forbidden -> throw KavitaException.Unauthorized
      HttpStatusCode.OK -> Unit
      else -> throw KavitaException.Unknown("HTTP ${response.status.value}")
    }
    val ct = response.contentType()
    if (ct == null || !ct.match(ContentType.Application.Json)) {
      throw KavitaException.UnexpectedResponse
    }
    return try {
      response.body()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      throw KavitaException.UnexpectedResponse
    }
  }
}
