package com.charliesbot.kanshu.features.home.di

import com.charliesbot.kanshu.features.home.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val homeModule = module { viewModel { HomeViewModel() } }
