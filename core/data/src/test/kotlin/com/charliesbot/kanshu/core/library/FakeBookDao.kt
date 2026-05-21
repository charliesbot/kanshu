package com.charliesbot.kanshu.core.library

import com.charliesbot.kanshu.core.database.dao.BookDao
import com.charliesbot.kanshu.core.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

// In-memory fake matching the BookDao surface. Backed by a single StateFlow<Map> so reads stay
// reactive and tests can seed rows directly. Mirrors the real DAO's contract for the cases the
// repository exercises — anything not used here is intentionally absent.
class FakeBookDao(initial: Map<String, BookEntity> = emptyMap()) : BookDao {
  private val rows = MutableStateFlow(initial)

  fun snapshot(): Map<String, BookEntity> = rows.value

  override fun observeAll(): Flow<List<BookEntity>> = rows.map { it.values.toList() }

  override fun observeDownloaded(): Flow<List<BookEntity>> =
    rows.map { it.values.filter { row -> row.localPath != null } }

  override suspend fun find(id: String): BookEntity? = rows.value[id]

  override suspend fun allDownloaded(): List<BookEntity> =
    rows.value.values.filter { it.localPath != null }

  override suspend fun getAll(): List<BookEntity> = rows.value.values.toList()

  override suspend fun upsert(book: BookEntity) {
    rows.update { it + (book.id to book) }
  }

  override suspend fun clearDownload(id: String) {
    rows.update { current ->
      val existing = current[id] ?: return@update current
      current + (id to existing.copy(localPath = null, byteSize = null, downloadedAt = null))
    }
  }

  override suspend fun delete(id: String) {
    rows.update { it - id }
  }
}
