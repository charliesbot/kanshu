package com.charliesbot.kanshu.core.di

import com.charliesbot.kanshu.core.connection.ConnectionRepository
import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.connection.CredentialsRepositoryImpl
import com.charliesbot.kanshu.core.connection.kavitaCredentialsDataStore
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaApiImpl
import com.charliesbot.kanshu.core.library.LibraryRepository
import com.charliesbot.kanshu.core.library.LibraryRepositoryImpl
import com.charliesbot.kanshu.core.library.usecase.LoadLibraryUseCase
import com.charliesbot.kanshu.core.network.buildKavitaHttpClient
import com.charliesbot.kanshu.core.reader.LocalAssetReaderSource
import com.charliesbot.kanshu.core.reader.ReaderSource
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import com.charliesbot.kanshu.core.security.KavitaApiKeyCipher
import com.charliesbot.kanshu.core.security.KeyCipher
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreModule = module {
  single { buildKavitaHttpClient() }
  single<KavitaApi> { KavitaApiImpl(get()) }
  single { ConnectionRepository(get()) }
  single { androidContext().kavitaCredentialsDataStore }
  single<KeyCipher> { KavitaApiKeyCipher() }
  single<CredentialsRepository> { CredentialsRepositoryImpl(get(), get()) }
  single<LibraryRepository> { LibraryRepositoryImpl(get(), get()) }
  factory { LoadLibraryUseCase(get()) }
  single<ReaderSource> { LocalAssetReaderSource(androidContext()) }
  factory { OpenBookUseCase(get()) }
}
