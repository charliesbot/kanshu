package com.charliesbot.kanshu.di

import com.charliesbot.kanshu.KanshuAppViewModel
import com.charliesbot.kanshu.core.di.coreModule
import com.charliesbot.kanshu.features.connection.di.connectionModule
import com.charliesbot.kanshu.features.library.di.libraryModule
import com.charliesbot.kanshu.features.reader.di.readerModule
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

private val kanshuAppModule = module { viewModel { KanshuAppViewModel(get()) } }

val appModule = listOf(coreModule, connectionModule, libraryModule, readerModule, kanshuAppModule)
