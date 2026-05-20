package com.charliesbot.kanshu.core.connection

import kotlinx.coroutines.flow.Flow

interface CredentialsRepository {
  val credentials: Flow<KavitaCredentials?>

  suspend fun save(credentials: KavitaCredentials)

  suspend fun clear()
}
