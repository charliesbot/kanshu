package com.charliesbot.kanshu.core.library

data class LibraryItem(
  val id: Int,
  val title: String,
  val coverUrl: String?,
  val downloadState: DownloadState = DownloadState.NotDownloaded,
)
