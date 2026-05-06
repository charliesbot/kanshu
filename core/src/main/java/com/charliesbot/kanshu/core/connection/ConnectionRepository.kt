package com.charliesbot.kanshu.core.connection

import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaException
import java.net.URI
import kotlinx.coroutines.CancellationException

class ConnectionRepository(private val api: KavitaApi) {
  suspend fun testConnection(baseUrl: String, apiKey: String): ConnectionTestResult {
    if (!isValidHttpUrl(baseUrl)) return ConnectionTestResult.InvalidUrl

    return try {
      val info = api.serverInfoSlim(baseUrl, apiKey)
      ConnectionTestResult.Ok(info.installVersion)
    } catch (e: CancellationException) {
      throw e
    } catch (e: KavitaException) {
      e.toResult()
    }
  }

  private fun isValidHttpUrl(value: String): Boolean {
    if (value.isBlank()) return false
    return try {
      val url = URI(value.trim()).toURL()
      url.protocol == "http" || url.protocol == "https"
    } catch (e: Exception) {
      false
    }
  }
}

private fun KavitaException.toResult(): ConnectionTestResult =
  when (this) {
    KavitaException.Unauthorized -> ConnectionTestResult.Unauthorized
    KavitaException.UnexpectedResponse -> ConnectionTestResult.UnexpectedResponse
    KavitaException.NetworkError -> ConnectionTestResult.NetworkError
    is KavitaException.Unknown -> ConnectionTestResult.Unexpected(message ?: "Unknown error")
  }
