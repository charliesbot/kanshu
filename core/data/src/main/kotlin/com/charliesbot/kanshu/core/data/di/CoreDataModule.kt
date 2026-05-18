package com.charliesbot.kanshu.core.data.di

import androidx.room.Room
import com.charliesbot.kanshu.core.connection.ConnectionRepository
import com.charliesbot.kanshu.core.connection.ConnectionRepositoryImpl
import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.connection.CredentialsRepositoryImpl
import com.charliesbot.kanshu.core.connection.kavitaCredentialsDataStore
import com.charliesbot.kanshu.core.database.KanshuDatabase
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaApiImpl
import com.charliesbot.kanshu.core.library.BookRepository
import com.charliesbot.kanshu.core.library.BookRepositoryImpl
import com.charliesbot.kanshu.core.library.usecase.DeleteDownloadUseCase
import com.charliesbot.kanshu.core.library.usecase.DownloadBookUseCase
import com.charliesbot.kanshu.core.library.usecase.LoadLibraryUseCase
import com.charliesbot.kanshu.core.network.buildKavitaHttpClient
import com.charliesbot.kanshu.core.reader.KavitaReaderSource
import com.charliesbot.kanshu.core.reader.ReaderSource
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import com.charliesbot.kanshu.core.security.KavitaApiKeyCipher
import com.charliesbot.kanshu.core.security.KeyCipher
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreDataModule = module {
  single { buildKavitaHttpClient() }
  single<KavitaApi> { KavitaApiImpl(get()) }
  single<ConnectionRepository> { ConnectionRepositoryImpl(get()) }
  single { androidContext().kavitaCredentialsDataStore }
  single<KeyCipher> { KavitaApiKeyCipher() }
  single<CredentialsRepository> { CredentialsRepositoryImpl(get(), get()) }
  single {
    Room.databaseBuilder(androidContext(), KanshuDatabase::class.java, KanshuDatabase.NAME).build()
  }
  single { get<KanshuDatabase>().bookDao() }
  single { get<KanshuDatabase>().readingProgressDao() }
  single { get<KanshuDatabase>().annotationDao() }
  single<BookRepository> {
    BookRepositoryImpl(
      credentialsRepository = get(),
      api = get(),
      booksDir = File(androidContext().filesDir, "books"),
      bookDao = get(),
    )
  }
  factory { LoadLibraryUseCase(get()) }
  factory { DownloadBookUseCase(get()) }
  factory { DeleteDownloadUseCase(get()) }
  single<ReaderSource> { KavitaReaderSource(androidContext(), get()) }
  factory { OpenBookUseCase(get()) }
}
