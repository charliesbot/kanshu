package com.charliesbot.kanshu.core.library

import com.charliesbot.kanshu.core.provider.BookId

data class LibraryItem(
  val bookId: BookId,
  val title: String,
  val coverUrl: String?,
  val downloadState: DownloadState = DownloadState.NotDownloaded,
)
