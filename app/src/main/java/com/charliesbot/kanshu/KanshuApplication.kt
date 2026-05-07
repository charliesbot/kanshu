package com.charliesbot.kanshu

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.charliesbot.kanshu.di.appModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KanshuApplication : Application(), ImageLoaderFactory {
  private val imageLoader: ImageLoader by inject()

  override fun onCreate() {
    super.onCreate()

    startKoin {
      androidContext(this@KanshuApplication)
      modules(appModule)
    }
  }

  override fun newImageLoader(): ImageLoader = imageLoader
}
