package com.charliesbot.kanshu.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charliesbot.kanshu.core.database.dao.AnnotationDao
import com.charliesbot.kanshu.core.database.dao.BookDao
import com.charliesbot.kanshu.core.database.dao.ReadingProgressDao
import com.charliesbot.kanshu.core.database.entity.AnnotationEntity
import com.charliesbot.kanshu.core.database.entity.BookEntity
import com.charliesbot.kanshu.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KanshuDatabaseTest {

  private lateinit var db: KanshuDatabase
  private lateinit var bookDao: BookDao
  private lateinit var progressDao: ReadingProgressDao
  private lateinit var annotationDao: AnnotationDao

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    db = Room.inMemoryDatabaseBuilder(context, KanshuDatabase::class.java).build()
    bookDao = db.bookDao()
    progressDao = db.readingProgressDao()
    annotationDao = db.annotationDao()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun bookUpsertAndFind() = runTest {
    val book = sampleBook("kavita:1")
    bookDao.upsert(book)
    assertEquals(book, bookDao.find("kavita:1"))
  }

  @Test
  fun observeDownloadedFiltersByLocalPath() = runTest {
    bookDao.upsert(sampleBook("kavita:1", localPath = "/path/a.epub"))
    bookDao.upsert(sampleBook("kavita:2", localPath = null))
    val downloaded = bookDao.observeDownloaded().first()
    assertEquals(listOf("kavita:1"), downloaded.map { it.id })
  }

  @Test
  fun clearDownloadNullsTheRowButKeepsIt() = runTest {
    bookDao.upsert(sampleBook("kavita:1", localPath = "/path/a.epub", byteSize = 100L))
    bookDao.clearDownload("kavita:1")
    val row = bookDao.find("kavita:1")
    assertNull(row?.localPath)
    assertNull(row?.byteSize)
    assertEquals("Foo", row?.title)
  }

  @Test
  fun deletingBookCascadesToProgress() = runTest {
    bookDao.upsert(sampleBook("kavita:1"))
    progressDao.upsert(sampleProgress("kavita:1"))
    bookDao.delete("kavita:1")
    assertNull(progressDao.find("kavita:1"))
  }

  @Test
  fun deletingBookCascadesToAnnotations() = runTest {
    bookDao.upsert(sampleBook("kavita:1"))
    annotationDao.upsert(sampleAnnotation("a-1", "kavita:1"))
    bookDao.delete("kavita:1")
    assertTrue(annotationDao.observeForBook("kavita:1").first().isEmpty())
  }

  @Test
  fun progressIsOneRowPerBookViaUpsert() = runTest {
    bookDao.upsert(sampleBook("kavita:1"))
    progressDao.upsert(sampleProgress("kavita:1", progression = 0.1))
    progressDao.upsert(sampleProgress("kavita:1", progression = 0.7))
    assertEquals(0.7, progressDao.find("kavita:1")?.progression!!, 0.0)
  }

  @Test
  fun annotationsOrderedByCreatedAt() = runTest {
    bookDao.upsert(sampleBook("kavita:1"))
    annotationDao.upsert(sampleAnnotation("a-2", "kavita:1", createdAt = 200))
    annotationDao.upsert(sampleAnnotation("a-1", "kavita:1", createdAt = 100))
    val ids = annotationDao.observeForBook("kavita:1").first().map { it.id }
    assertEquals(listOf("a-1", "a-2"), ids)
  }

  private fun sampleBook(
    id: String,
    localPath: String? = null,
    byteSize: Long? = null,
  ): BookEntity =
    BookEntity(
      id = id,
      source = "kavita",
      sourceItemId = id.substringAfter(":"),
      title = "Foo",
      localPath = localPath,
      byteSize = byteSize,
      downloadedAt = if (localPath != null) 1000L else null,
      lastOpenedAt = null,
    )

  private fun sampleProgress(bookId: String, progression: Double = 0.0): ReadingProgressEntity =
    ReadingProgressEntity(
      bookId = bookId,
      locatorJson = """{"href":"chapter1.xhtml"}""",
      progression = progression,
      updatedAt = 1000L,
      syncMetadata = null,
    )

  private fun sampleAnnotation(id: String, bookId: String, createdAt: Long = 0L): AnnotationEntity =
    AnnotationEntity(
      id = id,
      bookId = bookId,
      locatorJson = """{"href":"chapter1.xhtml"}""",
      selectedText = "hello",
      color = null,
      noteBody = null,
      containsSpoiler = false,
      createdAt = createdAt,
      updatedAt = createdAt,
    )
}
