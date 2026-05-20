package com.charliesbot.kanshu.core.connection

interface ConnectionRepository {
  suspend fun testConnection(baseUrl: String, apiKey: String): ConnectionTestResult
}
