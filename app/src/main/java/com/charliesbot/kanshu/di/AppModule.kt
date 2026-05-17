package com.charliesbot.kanshu.di

import com.charliesbot.kanshu.KanshuAppViewModel
import com.charliesbot.kanshu.core.data.di.coreDataModule
import com.charliesbot.kanshu.features.connection.di.connectionModule
import com.charliesbot.kanshu.features.library.di.libraryModule
import com.charliesbot.kanshu.features.reader.di.readerModule
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

private val kanshuAppModule = module { viewModel { KanshuAppViewModel(get()) } }

val appModule =
  listOf(coreDataModule, connectionModule, libraryModule, readerModule, kanshuAppModule)
