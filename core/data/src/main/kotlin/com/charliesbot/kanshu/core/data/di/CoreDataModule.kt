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
import com.charliesbot.kanshu.core.provider.ProviderRegistry
import com.charliesbot.kanshu.core.provider.ProviderRegistryImpl
import com.charliesbot.kanshu.core.provider.kavita.KavitaProvider
import com.charliesbot.kanshu.core.reader.EpubOpener
import com.charliesbot.kanshu.core.reader.EpubOpenerImpl
import com.charliesbot.kanshu.core.reader.ReaderPreferencesRepository
import com.charliesbot.kanshu.core.reader.annotation.AnnotationRepository
import com.charliesbot.kanshu.core.reader.annotation.AnnotationRepositoryImpl
import com.charliesbot.kanshu.core.reader.preferences.ReaderPreferencesRepositoryImpl
import com.charliesbot.kanshu.core.reader.preferences.readerPreferencesDataStore
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import com.charliesbot.kanshu.core.security.KavitaApiKeyCipher
import com.charliesbot.kanshu.core.security.KeyCipher
import com.charliesbot.kanshu.core.sync.ProgressRepository
import com.charliesbot.kanshu.core.sync.ProgressRepositoryImpl
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
    Room.databaseBuilder(androidContext(), KanshuDatabase::class.java, KanshuDatabase.NAME)
      // Early-stage personal app: incompatible schemas reset instead of carrying migrations.
      .fallbackToDestructiveMigration(dropAllTables = true)
      .build()
  }
  single { get<KanshuDatabase>().bookDao() }
  single { get<KanshuDatabase>().readingProgressDao() }
  single { get<KanshuDatabase>().annotationDao() }
  single { KavitaProvider(credentials = get(), api = get()) }
  single<ProviderRegistry> { ProviderRegistryImpl(listOf(get<KavitaProvider>())) }
  single<BookRepository> {
    BookRepositoryImpl(
      providers = get(),
      booksDir = File(androidContext().filesDir, "books"),
      bookDao = get(),
    )
  }
  factory { LoadLibraryUseCase(get()) }
  factory { DownloadBookUseCase(get()) }
  factory { DeleteDownloadUseCase(get()) }
  single<EpubOpener> { EpubOpenerImpl(androidContext(), get()) }
  factory { OpenBookUseCase(get()) }
  // Construct the reader-prefs repo by handing it the context-bound DataStore directly so we
  // avoid registering a second DataStore<Preferences> singleton (would conflict with the
  // credentials DataStore on get<DataStore<Preferences>>()).
  single<ReaderPreferencesRepository> {
    ReaderPreferencesRepositoryImpl(androidContext().readerPreferencesDataStore)
  }

  single<ProgressRepository> {
    ProgressRepositoryImpl(providers = get(), books = get(), progressDao = get())
  }
  single<AnnotationRepository> { AnnotationRepositoryImpl(annotationDao = get()) }
}
