package com.charliesbot.kanshu.core.kavita

import android.util.Log
import com.charliesbot.kanshu.core.kavita.dto.SeriesDto
import com.charliesbot.kanshu.core.kavita.dto.ServerInfoSlim
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
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import kotlinx.coroutines.CancellationException

private const val TAG = "KavitaApi"

interface KavitaApi {
  suspend fun serverInfoSlim(baseUrl: String, apiKey: String): ServerInfoSlim

  suspend fun listSeries(
    baseUrl: String,
    apiKey: String,
    pageNumber: Int,
    pageSize: Int,
  ): List<SeriesDto>
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

  private suspend fun executeRequest(
    url: String,
    httpMethod: HttpMethod,
    block: HttpRequestBuilder.() -> Unit,
  ): HttpResponse =
    try {
      client.request(url) {
        method = httpMethod
        block()
      }
    } catch (e: CancellationException) {
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
