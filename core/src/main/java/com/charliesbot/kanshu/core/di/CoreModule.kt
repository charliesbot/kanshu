package com.charliesbot.kanshu.core.di

import com.charliesbot.kanshu.core.connection.ConnectionRepository
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaApiImpl
import com.charliesbot.kanshu.core.network.buildKavitaHttpClient
import org.koin.dsl.module

val coreModule = module {
  single { buildKavitaHttpClient() }
  single<KavitaApi> { KavitaApiImpl(get()) }
  single { ConnectionRepository(get()) }
}
