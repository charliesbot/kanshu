package com.charliesbot.kanshu.core.ui.image

import android.content.Context
import coil.ImageLoader

fun buildKanshuImageLoader(context: Context): ImageLoader =
  ImageLoader.Builder(context).crossfade(false).respectCacheHeaders(true).build()
