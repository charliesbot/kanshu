package com.charliesbot.kanshu.features.connection.di

import com.charliesbot.kanshu.features.connection.ConnectionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val connectionModule = module { viewModel { ConnectionViewModel(get(), get()) } }
