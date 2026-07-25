package com.charliesbot.kanshu.core.sync

import com.charliesbot.kanshu.core.database.dao.ReadingProgressDao
import com.charliesbot.kanshu.core.database.entity.ReadingProgressEntity
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the reader's resume lookup: the stored JSON is what the reader actually reopens from, so
 * decoding is exercised against real row content rather than in-memory objects.
 */
class SyncRepositoryLocalPositionTest {

  @Test
  fun `decodes a stored row`() = runTest {
    val repository = repositoryWith("""{"spineIndex":3,"charOffset":512,"progressInSpine":0.25}""")

    val position = repository.localPosition("kavita:1")

    assertEquals(
      ReaderPosition(spineIndex = 3, charOffset = 512, progressInSpine = 0.25f),
      position,
    )
  }

  @Test
  fun `a page-index row resumes at the chapter start`() = runTest {
    // Exactly the shape the page-index build wrote, stale pageIndex field and all.
    val repository =
      repositoryWith("""{"schemaVersion":1,"spineIndex":7,"pageIndex":12,"progressInSpine":0.4}""")

    val position = repository.localPosition("kavita:1")

    assertEquals(7, position?.spineIndex)
    assertEquals(0, position?.charOffset)
    assertEquals(0.4f, position?.progressInSpine)
  }

  @Test
  fun `missing row resumes from the beginning`() = runTest {
    val repository = repositoryWith(null)

    assertNull(repository.localPosition("kavita:1"))
  }

  @Test
  fun `corrupt row falls back to the book start instead of failing the open`() = runTest {
    val repository = repositoryWith("not json at all")

    val position = repository.localPosition("kavita:1")

    assertEquals(0, position?.spineIndex)
    assertEquals(0, position?.charOffset)
  }

  private fun repositoryWith(locatorJson: String?): SyncRepository {
    val dao =
      mockk<ReadingProgressDao> {
        coEvery { find(any()) } returns
          locatorJson?.let {
            ReadingProgressEntity(
              bookId = "kavita:1",
              locatorJson = it,
              progression = 0.0,
              updatedAt = 0L,
              syncMetadata = null,
            )
          }
      }
    return SyncRepositoryImpl(progressSync = mockk(), progressDao = dao)
  }
}
