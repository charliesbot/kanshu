package com.charliesbot.kanshu.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charliesbot.kanshu.core.database.dao.BookDao
import com.charliesbot.kanshu.core.database.dao.HighlightDao
import com.charliesbot.kanshu.core.database.dao.ReadingProgressDao
import com.charliesbot.kanshu.core.database.entity.BookEntity
import com.charliesbot.kanshu.core.database.entity.HighlightEntity
import com.charliesbot.kanshu.core.database.entity.ReadingProgressEntity
import com.charliesbot.kanshu.core.reader.highlight.HighlightSyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KanshuDatabaseTest {

  private lateinit var db: KanshuDatabase
  private lateinit var bookDao: BookDao
  private lateinit var progressDao: ReadingProgressDao
  private lateinit var highlightDao: HighlightDao

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    db = Room.inMemoryDatabaseBuilder(context, KanshuDatabase::class.java).build()
    bookDao = db.bookDao()
    progressDao = db.readingProgressDao()
    highlightDao = db.highlightDao()
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
  fun providerBookKeyIsUniqueWithinAnInstance() = runTest {
    bookDao.upsert(sampleBook("kavita:1"))

    assertThrows(SQLiteConstraintException::class.java) {
      db.openHelper.writableDatabase.execSQL(
        """
        INSERT INTO books (
          id, provider_instance_id, provider_item_id, title
        ) VALUES ('another-id', 'kavita', '1', 'Duplicate')
        """
          .trimIndent()
      )
    }
  }

  @Test
  fun providerItemIdsMayRepeatAcrossInstances() = runTest {
    bookDao.upsert(sampleBook("kavita:1"))
    bookDao.upsert(sampleBook("other:1", providerInstanceId = "other"))

    assertEquals(2, bookDao.getAll().size)
  }

  @Test
  fun deletingBookCascadesToProgress() = runTest {
    bookDao.upsert(sampleBook("kavita:1"))
    progressDao.upsert(sampleProgress("kavita:1"))
    bookDao.delete("kavita:1")
    assertNull(progressDao.find("kavita:1"))
  }

  @Test
  fun deletingBookCascadesToHighlights() = runTest {
    bookDao.upsert(sampleBook("kavita:1"))
    highlightDao.upsert(sampleHighlight("a-1", "kavita:1"))
    bookDao.delete("kavita:1")
    assertTrue(highlightDao.observeForSpine("kavita:1", 0).first().isEmpty())
  }

  @Test
  fun progressIsOneRowPerBookViaUpsert() = runTest {
    bookDao.upsert(sampleBook("kavita:1"))
    progressDao.upsert(sampleProgress("kavita:1", progression = 0.1))
    progressDao.upsert(sampleProgress("kavita:1", progression = 0.7))
    assertEquals(0.7, progressDao.find("kavita:1")?.progression!!, 0.0)
  }

  @Test
  fun highlightsForASpineItemAreOrderedByOffset() = runTest {
    bookDao.upsert(sampleBook("kavita:1"))
    highlightDao.upsert(sampleHighlight("late", "kavita:1", startCharOffset = 900))
    highlightDao.upsert(sampleHighlight("early", "kavita:1", startCharOffset = 100))
    // A different chapter, to pin the spine filter against a real database.
    highlightDao.upsert(sampleHighlight("other", "kavita:1", spineIndex = 4))

    val ids = highlightDao.observeForSpine("kavita:1", 0).first().map { it.id }

    assertEquals(listOf("early", "late"), ids)
  }

  @Test
  fun highlightQueriesUseTypedSyncStates() = runTest {
    bookDao.upsert(sampleBook("kavita:1"))
    highlightDao.upsert(
      sampleHighlight("pending", "kavita:1").copy(syncState = HighlightSyncState.PENDING_UPSERT)
    )
    highlightDao.upsert(
      sampleHighlight("deleted", "kavita:1").copy(syncState = HighlightSyncState.PENDING_DELETE)
    )

    assertEquals(
      listOf("pending"),
      highlightDao.pending("kavita:1", HighlightSyncState.PENDING_UPSERT).map { it.id },
    )
    assertEquals(
      listOf("pending"),
      highlightDao.observeForSpine("kavita:1", 0).first().map { it.id },
    )
  }

  private fun sampleBook(
    id: String,
    localPath: String? = null,
    byteSize: Long? = null,
    providerInstanceId: String = "kavita",
    providerItemId: String = id.substringAfter(":"),
  ): BookEntity =
    BookEntity(
      id = id,
      providerInstanceId = providerInstanceId,
      providerItemId = providerItemId,
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
    )

  private fun sampleHighlight(
    id: String,
    bookId: String,
    createdAt: Long = 0L,
    spineIndex: Int = 0,
    startCharOffset: Int = 0,
  ): HighlightEntity =
    HighlightEntity(
      id = id,
      bookId = bookId,
      spineIndex = spineIndex,
      startCharOffset = startCharOffset,
      endCharOffset = startCharOffset + 5,
      selectedText = "hello",
      createdAt = createdAt,
      updatedAt = createdAt,
    )
}
