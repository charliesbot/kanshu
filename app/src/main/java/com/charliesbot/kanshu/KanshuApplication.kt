package com.charliesbot.kanshu

import android.app.Application
import com.charliesbot.kanshu.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KanshuApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    startKoin {
      androidContext(this@KanshuApplication)
      modules(appModule)
    }
  }
}
