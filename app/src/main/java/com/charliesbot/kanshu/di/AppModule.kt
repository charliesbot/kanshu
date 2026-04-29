package com.charliesbot.kanshu.di

import com.charliesbot.kanshu.core.di.coreModule
import com.charliesbot.kanshu.features.home.di.homeModule

val appModule = listOf(coreModule, homeModule)
