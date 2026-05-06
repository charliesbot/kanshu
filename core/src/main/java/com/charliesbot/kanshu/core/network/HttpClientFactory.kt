package com.charliesbot.kanshu.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun buildKavitaHttpClient(): HttpClient =
  HttpClient(OkHttp) {
    expectSuccess = false
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout) {
      requestTimeoutMillis = 10_000
      connectTimeoutMillis = 5_000
      socketTimeoutMillis = 10_000
    }
  }
