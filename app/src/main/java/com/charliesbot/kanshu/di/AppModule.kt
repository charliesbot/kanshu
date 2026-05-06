package com.charliesbot.kanshu.di

import com.charliesbot.kanshu.core.di.coreModule
import com.charliesbot.kanshu.features.connection.di.connectionModule

val appModule = listOf(coreModule, connectionModule)
