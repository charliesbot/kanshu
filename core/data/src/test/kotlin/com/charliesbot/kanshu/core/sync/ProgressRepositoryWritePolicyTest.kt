package com.charliesbot.kanshu.core.sync

import com.charliesbot.kanshu.core.database.dao.BookDao
import com.charliesbot.kanshu.core.database.dao.ReadingProgressDao
import com.charliesbot.kanshu.core.database.entity.BookEntity
import com.charliesbot.kanshu.core.database.entity.ReadingProgressEntity
import com.charliesbot.kanshu.core.provider.AcquiredBook
import com.charliesbot.kanshu.core.provider.BookId
import com.charliesbot.kanshu.core.provider.Provider
import com.charliesbot.kanshu.core.provider.ProviderBook
import com.charliesbot.kanshu.core.provider.ProviderBookContext
import com.charliesbot.kanshu.core.provider.ProviderBookKey
import com.charliesbot.kanshu.core.provider.ProviderCapabilities
import com.charliesbot.kanshu.core.provider.ProviderCover
import com.charliesbot.kanshu.core.provider.ProviderDescriptor
import com.charliesbot.kanshu.core.provider.ProviderError
import com.charliesbot.kanshu.core.provider.ProviderInstanceId
import com.charliesbot.kanshu.core.provider.ProviderRegistryImpl
import com.charliesbot.kanshu.core.provider.ProviderResult
import com.charliesbot.kanshu.core.provider.ProviderType
import com.charliesbot.kanshu.core.provider.RemoteProgress
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressRepositoryWritePolicyTest {

  private val bookId = BookId("test:1")
  private val file = File("book.epub")

  // Four spine items, so progressionIn is (spineIndex + progressInSpine) / 4.
  private val publication: Publication =
    mockk(relaxUnitFun = true) {
      every { readingOrder } returns List(4) { mockk<Link>(relaxed = true) }
    }

  private val resumed = ReaderPosition(spineIndex = 1, charOffset = 400, progressInSpine = 0.5f)

  @Test
  fun `a moved position is written and pushed`() = runTest {
    val sync = FakeProgressProvider()
    val dao = daoWith(resumed)
    val repository = repository(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))
    val moved = resumed.copy(charOffset = 900, progressInSpine = 0.75f)

    repository.localPosition(bookId)
    repository.setProgress(bookId, file, moved, publication)
    advanceUntilIdle()

    coVerify(exactly = 1) { dao.upsert(any()) }
    assertEquals(listOf(moved), sync.pushed)
    assertEquals(ProviderBookKey(ProviderInstanceId("test"), "1"), sync.contexts.last().book)
  }

  @Test
  fun `provider-defined no-op keeps progress local`() = runTest {
    val provider = FakeProgressProvider(progressSync = false)
    val dao = daoWith(resumed)
    val repository =
      repository(provider, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))

    repository.setProgress(bookId, file, resumed, publication)
    advanceUntilIdle()

    coVerify(exactly = 1) { dao.upsert(any()) }
    assertEquals(1, provider.pullCalls)
    assertEquals(1, provider.pushCalls)
    assertTrue(provider.contexts.isEmpty())
    assertTrue(provider.pushed.isEmpty())
  }

  @Test
  fun `a push that would overwrite a further-along remote is skipped`() = runTest {
    // Remote at 80% of the book against a local position at (1 + 0.75) / 4 = 43.75%.
    val sync = FakeProgressProvider(remote = remoteAt(0.8))
    val dao = daoWith(resumed)
    val repository = repository(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))

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
    val sync = FakeProgressProvider(remote = remoteAt(0.1))
    val dao = daoWith(resumed)
    val repository = repository(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))
    val moved = resumed.copy(charOffset = 900, progressInSpine = 0.75f)

    repository.localPosition(bookId)
    repository.setProgress(bookId, file, moved, publication)
    advanceUntilIdle()

    assertEquals(listOf(moved), sync.pushed)
  }

  @Test
  fun `a push proceeds when the remote cannot be read`() = runTest {
    val sync = FakeProgressProvider(pullResult = Result.failure(RuntimeException("offline")))
    val dao = daoWith(resumed)
    val repository = repository(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))
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
    val sync = FakeProgressProvider(remote = remoteAt(0.8))
    val dao = daoWith(ReaderPosition(spineIndex = 1, charOffset = 150, progressInSpine = 0.375f))
    val repository = repository(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))

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
    // Provider implementations do work outside their ProviderResult boundary — credential reads
    // and file hashing — so pull can throw rather than return a failure.
    val sync = FakeProgressProvider(pullThrows = true)
    val dao = daoWith(resumed)
    val repository = repository(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))
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
    val sync = FakeProgressProvider(remote = remoteAt(0.8).copy(position = null))
    val dao = daoWith(resumed)
    val repository = repository(sync, dao, CoroutineScope(StandardTestDispatcher(testScheduler)))

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
            bookId = bookId.value,
            locatorJson = it,
            progression = 0.0,
            updatedAt = 0L,
            syncMetadata = null,
          )
        }
    }

  private fun repository(
    provider: Provider,
    progressDao: ReadingProgressDao,
    scope: CoroutineScope,
  ): ProgressRepositoryImpl {
    val books =
      mockk<BookDao> {
        coEvery { find(bookId.value) } returns
          BookEntity(
            id = bookId.value,
            providerInstanceId = "test",
            providerItemId = "1",
            title = "Book",
            localPath = file.path,
            byteSize = 1,
            downloadedAt = 1,
            lastOpenedAt = null,
          )
      }
    return ProgressRepositoryImpl(ProviderRegistryImpl(listOf(provider)), books, progressDao, scope)
  }
}

private class FakeProgressProvider(
  private val remote: RemoteProgress? = null,
  private val pullResult: Result<RemoteProgress?>? = null,
  private val pullThrows: Boolean = false,
  private val progressSync: Boolean = true,
) : Provider {
  val pushed = mutableListOf<ReaderPosition>()
  val contexts = mutableListOf<ProviderBookContext>()
  var pullCalls = 0
  var pushCalls = 0

  override val descriptor =
    ProviderDescriptor(
      id = ProviderInstanceId("test"),
      type = ProviderType.LOCAL,
      displayName = "Test",
      enabled = true,
      capabilities = ProviderCapabilities(progressSync = progressSync, highlightSync = false),
    )

  override suspend fun fetchCatalog(): ProviderResult<List<ProviderBook>> =
    ProviderResult.Success(emptyList())

  override suspend fun resolveCover(
    book: ProviderBookKey,
    revisionToken: String?,
  ): ProviderCover? = null

  override suspend fun acquire(
    book: ProviderBookKey,
    target: File,
    onProgress: (downloaded: Long, total: Long?) -> Unit,
  ): ProviderResult<AcquiredBook> = ProviderResult.Failure(ProviderError.Network)

  override suspend fun pushProgress(
    context: ProviderBookContext,
    position: ReaderPosition,
  ): ProviderResult<Unit> {
    pushCalls += 1
    if (!progressSync) return super.pushProgress(context, position)
    contexts += context
    pushed += position
    return ProviderResult.Success(Unit)
  }

  override suspend fun pullProgress(context: ProviderBookContext): ProviderResult<RemoteProgress?> {
    pullCalls += 1
    if (!progressSync) return super.pullProgress(context)
    contexts += context
    if (pullThrows) throw IllegalStateException("credentials unavailable")
    return (pullResult ?: Result.success(remote)).fold(
      onSuccess = { ProviderResult.Success(it) },
      onFailure = { ProviderResult.Failure(ProviderError.Unknown(it.message)) },
    )
  }
}
