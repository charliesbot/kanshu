package com.charliesbot.kanshu.features.reader.di

import com.charliesbot.kanshu.features.reader.ReaderViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val readerModule = module {
  viewModel { (seriesId: Int) -> ReaderViewModel(seriesId, get(), get(), get()) }
}
