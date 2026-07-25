package com.charliesbot.kanshu.core.sync

import com.charliesbot.kanshu.core.database.dao.ReadingProgressDao
import com.charliesbot.kanshu.core.database.entity.ReadingProgressEntity
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

/**
 * Covers what the sync layer decides to push: the reader reports where it is, and a push that would
 * overwrite a further-along remote is skipped.
 */
class SyncRepositoryWritePolicyTest {

  private val bookId = "kavita:1"
  private val file = File("book.epub")

  // Four spine items, so progressionIn is (spineIndex + progressInSpine) / 4.
  private val publication: Publication =
    mockk(relaxUnitFun = true) {
      every { readingOrder } returns List(4) { mockk<Link>(relaxed = true) }
    }

  private val resumed = ReaderPosition(spineIndex = 1, charOffset = 400, progressInSpine = 0.5f)

  @Test
  fun `a moved position is written and pushed`() = runTest {
    val sync = FakeProgressSync()
    val dao = daoWith(resumed)
    val repository =
      SyncRepositoryImpl(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))
    val moved = resumed.copy(charOffset = 900, progressInSpine = 0.75f)

    repository.localPosition(bookId)
    repository.setProgress(bookId, file, moved, publication)
    advanceUntilIdle()

    coVerify(exactly = 1) { dao.upsert(any()) }
    assertEquals(listOf(moved), sync.pushed)
  }

  @Test
  fun `a push that would overwrite a further-along remote is skipped`() = runTest {
    // Remote at 80% of the book against a local position at (1 + 0.75) / 4 = 43.75%.
    val sync = FakeProgressSync(remote = remoteAt(0.8))
    val dao = daoWith(resumed)
    val repository =
      SyncRepositoryImpl(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))

    repository.localPosition(bookId)
    repository.setProgress(
      bookId,
      file,
      resumed.copy(charOffset = 900, progressInSpine = 0.75f),
      publication,
    )
    advanceUntilIdle()

    // The local write still happens — the DB is this device's truth. Only the destructive half is
    // suppressed.
    coVerify(exactly = 1) { dao.upsert(any()) }
    assertTrue(sync.pushed.isEmpty())
  }

  @Test
  fun `a push proceeds when the remote is behind`() = runTest {
    val sync = FakeProgressSync(remote = remoteAt(0.1))
    val dao = daoWith(resumed)
    val repository =
      SyncRepositoryImpl(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))
    val moved = resumed.copy(charOffset = 900, progressInSpine = 0.75f)

    repository.localPosition(bookId)
    repository.setProgress(bookId, file, moved, publication)
    advanceUntilIdle()

    assertEquals(listOf(moved), sync.pushed)
  }

  @Test
  fun `a push proceeds when the remote cannot be read`() = runTest {
    val sync = FakeProgressSync(pullResult = Result.failure(RuntimeException("offline")))
    val dao = daoWith(resumed)
    val repository =
      SyncRepositoryImpl(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))
    val moved = resumed.copy(charOffset = 900, progressInSpine = 0.75f)

    repository.localPosition(bookId)
    repository.setProgress(bookId, file, moved, publication)
    advanceUntilIdle()

    // Failing closed would mean a device that is usually offline never syncs at all.
    assertEquals(listOf(moved), sync.pushed)
  }

  @Test
  fun `a resume landing behind the stored offset is written locally but not pushed`() = runTest {
    // Typography changed between sessions, so the reader lands on the page containing the stored
    // offset, which starts before it. That is a genuinely different place, so the dedup cannot
    // catch it — the remote check is what stops the regression reaching the server.
    val sync = FakeProgressSync(remote = remoteAt(0.8))
    val dao = daoWith(ReaderPosition(spineIndex = 1, charOffset = 150, progressInSpine = 0.375f))
    val repository =
      SyncRepositoryImpl(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))

    repository.localPosition(bookId)
    repository.setProgress(
      bookId,
      file,
      ReaderPosition(spineIndex = 1, charOffset = 100, progressInSpine = 0.25f),
      publication,
    )
    advanceUntilIdle()

    coVerify(exactly = 1) { dao.upsert(any()) }
    assertTrue(sync.pushed.isEmpty())
  }

  @Test
  fun `a push proceeds when the remote check throws`() = runTest {
    // ProgressSync implementations do work outside their Result boundary — credential reads, file
    // hashing — so pull can throw rather than return a failed Result.
    val sync = FakeProgressSync(pullThrows = true)
    val dao = daoWith(resumed)
    val repository =
      SyncRepositoryImpl(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))
    val moved = resumed.copy(charOffset = 900)

    repository.localPosition(bookId)
    repository.setProgress(bookId, file, moved, publication)
    advanceUntilIdle()

    assertEquals(listOf(moved), sync.pushed)
  }

  @Test
  fun `an undecodable remote position still guards the push`() = runTest {
    // Another kosync client's XPointer, or Kavita's numeric-only PDF form: the position cannot be
    // mapped to our spine model, but the percentage is perfectly usable and says the remote is
    // further along.
    val sync = FakeProgressSync(remote = remoteAt(0.8).copy(position = null))
    val dao = daoWith(resumed)
    val repository =
      SyncRepositoryImpl(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))

    repository.localPosition(bookId)
    repository.setProgress(bookId, file, resumed.copy(charOffset = 900), publication)
    advanceUntilIdle()

    assertTrue(sync.pushed.isEmpty())
  }

  private fun remoteAt(percentage: Double) =
    RemoteProgress(
      position = ReaderPosition(spineIndex = 3, charOffset = 0, progressInSpine = 0f),
      percentage = percentage,
      timestampMillis = 0L,
      deviceName = "other device",
    )

  private fun daoWith(position: ReaderPosition?): ReadingProgressDao =
    daoWithJson(
      position?.let {
        """{"spineIndex":${it.spineIndex},"charOffset":${it.charOffset},""" +
          """"progressInSpine":${it.progressInSpine}}"""
      }
    )

  private fun daoWithJson(locatorJson: String?): ReadingProgressDao =
    mockk(relaxUnitFun = true) {
      coEvery { find(any()) } returns
        locatorJson?.let {
          ReadingProgressEntity(
            bookId = bookId,
            locatorJson = it,
            progression = 0.0,
            updatedAt = 0L,
            syncMetadata = null,
          )
        }
    }
}

private class FakeProgressSync(
  private val remote: RemoteProgress? = null,
  private val pullResult: Result<RemoteProgress?>? = null,
  private val pullThrows: Boolean = false,
) : ProgressSync {
  val pushed = mutableListOf<ReaderPosition>()

  override suspend fun push(
    file: File,
    position: ReaderPosition,
    publication: Publication,
    timestampMillis: Long,
  ): Result<Unit> {
    pushed += position
    return Result.success(Unit)
  }

  override suspend fun pull(file: File, publication: Publication): Result<RemoteProgress?> {
    if (pullThrows) throw IllegalStateException("credentials unavailable")
    return pullResult ?: Result.success(remote)
  }
}
