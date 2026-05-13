package com.charliesbot.kanshu.features.library.di

import com.charliesbot.kanshu.features.library.LibraryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val libraryModule = module { viewModel { LibraryViewModel(get(), get(), get()) } }
