package com.charliesbot.kanshu.core.reader

import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication

// Wraps a Readium Publication so consumers (the reader feature) never import Readium types
// directly. Chapters are addressed by index; bytes come back as the raw resource contents.
class BookHandle internal constructor(private val publication: Publication) : Closeable {
  val title: String?
    get() = publication.metadata.title

  val chapterCount: Int
    get() = publication.readingOrder.size

  // Resource.read does file IO and zip inflate — keep it off the caller's dispatcher so the
  // ViewModel can launch on viewModelScope (Main) without blocking the UI.
  suspend fun chapterBytes(index: Int): ByteArray? =
    withContext(Dispatchers.IO) {
      val link = publication.readingOrder.getOrNull(index) ?: return@withContext null
      val resource = publication.get(link) ?: return@withContext null
      try {
        resource.read().getOrNull()
      } finally {
        resource.close()
      }
    }

  override fun close() {
    publication.close()
  }
}
