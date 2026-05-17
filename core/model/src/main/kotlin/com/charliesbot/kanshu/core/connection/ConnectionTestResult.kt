package com.charliesbot.kanshu.core.connection

sealed interface ConnectionTestResult {
  data class Ok(val serverVersion: String?) : ConnectionTestResult

  data object InvalidUrl : ConnectionTestResult

  data object Unauthorized : ConnectionTestResult

  data object UnexpectedResponse : ConnectionTestResult

  data object NetworkError : ConnectionTestResult

  data class Unexpected(val message: String) : ConnectionTestResult
}
