package com.charliesbot.kanshu.core.kavita

import android.util.Log
import com.charliesbot.kanshu.core.kavita.dto.ServerInfoSlim
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import kotlinx.coroutines.CancellationException

private const val TAG = "KavitaApi"

interface KavitaApi {
  suspend fun serverInfoSlim(baseUrl: String, apiKey: String): ServerInfoSlim
}

class KavitaApiImpl(private val client: HttpClient) : KavitaApi {
  override suspend fun serverInfoSlim(baseUrl: String, apiKey: String): ServerInfoSlim {
    val response = executeGet("${baseUrl.trimEnd('/')}/api/Server/server-info-slim", apiKey)
    return decodeJsonBody(response)
  }

  private suspend fun executeGet(url: String, apiKey: String): HttpResponse =
    try {
      client.get(url) { header("x-api-key", apiKey) }
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
