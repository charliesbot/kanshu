package com.charliesbot.kanshu.core.library

sealed interface DownloadState {
  data object NotDownloaded : DownloadState

  // Integer percent in 0..100. Progress is throttled to integer-percent changes by the repo to
  // avoid burning e-ink refresh cycles on every chunk that lands.
  data class Downloading(val progress: Int) : DownloadState

  data object Downloaded : DownloadState
}
