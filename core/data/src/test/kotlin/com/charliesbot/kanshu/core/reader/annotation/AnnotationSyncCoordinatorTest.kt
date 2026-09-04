package com.charliesbot.kanshu.core.reader.annotation

import com.charliesbot.kanshu.core.database.dao.BookDao
import com.charliesbot.kanshu.core.database.entity.BookEntity
import com.charliesbot.kanshu.core.provider.AcquiredBook
import com.charliesbot.kanshu.core.provider.BookId
import com.charliesbot.kanshu.core.provider.HighlightChange
import com.charliesbot.kanshu.core.provider.HighlightPushAck
import com.charliesbot.kanshu.core.provider.Provider
import com.charliesbot.kanshu.core.provider.ProviderBook
import com.charliesbot.kanshu.core.provider.ProviderBookKey
import com.charliesbot.kanshu.core.provider.ProviderCapabilities
import com.charliesbot.kanshu.core.provider.ProviderCover
import com.charliesbot.kanshu.core.provider.ProviderDescriptor
import com.charliesbot.kanshu.core.provider.ProviderHighlightContext
import com.charliesbot.kanshu.core.provider.ProviderHighlightSnapshot
import com.charliesbot.kanshu.core.provider.ProviderInstanceId
import com.charliesbot.kanshu.core.provider.ProviderRegistryImpl
import com.charliesbot.kanshu.core.provider.ProviderResult
import com.charliesbot.kanshu.core.provider.ProviderType
import com.charliesbot.kanshu.core.reader.ReaderHighlightColor
import com.charliesbot.kanshu.core.reader.SourceElementPath
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.readium.r2.shared.publication.Publication

class AnnotationSyncCoordinatorTest {
  @Test
  fun pushesDeletesThenUpsertsThenPulls() = runTest {
    val events = mutableListOf<String>()
    val delete = HighlightChange.Delete("delete", "remote-delete", 3L)
    val upsert =
      HighlightChange.Upsert(
        localId = "upsert",
        remoteId = null,
        expectedUpdatedAt = 4L,
        spineIndex = 0,
        startCharOffset = 1,
        endCharOffset = 2,
        selectedText = "x",
        startElementPath = SourceElementPath(listOf(0)),
        endElementPath = SourceElementPath(listOf(0)),
        color = ReaderHighlightColor.Yellow,
        createdAt = 1L,
      )
    val annotations =
      mockk<AnnotationRepository>(relaxed = true) {
        coEvery { pendingChanges("kavita:7", HighlightSyncState.PENDING_DELETE) } returns
          listOf(delete)
        coEvery { pendingChanges("kavita:7", HighlightSyncState.PENDING_UPSERT) } returns
          listOf(upsert)
        coEvery { acknowledgeDelete("delete", 3L) } answers { events += "ack-delete" }
        coEvery { acknowledgeUpsert("upsert", 4L, "remote-created") } answers
          {
            events += "ack-upsert"
          }
        coEvery { applySnapshot("kavita:7", any()) } answers { events += "apply-pull" }
      }
    val provider =
      RecordingProvider(events) { change ->
        when (change) {
          is HighlightChange.Delete -> ProviderResult.Success(HighlightPushAck())
          is HighlightChange.Upsert ->
            ProviderResult.Success(HighlightPushAck(remoteId = "remote-created"))
        }
      }
    val coordinator = coordinator(provider, annotations)

    coordinator.synchronize(
      BookId("kavita:7"),
      File("book.epub"),
      mockk<Publication>(),
    ) {
      null
    }

    assertEquals(
      listOf("push-delete", "ack-delete", "push-upsert", "ack-upsert", "pull", "apply-pull"),
      events,
    )
  }

  @Test
  fun failedPushLeavesPendingStateUntouchedAndStillCompletesPull() = runTest {
    val change = HighlightChange.Delete("delete", "remote-delete", 3L)
    val annotations =
      mockk<AnnotationRepository>(relaxed = true) {
        coEvery { pendingChanges("kavita:7", HighlightSyncState.PENDING_DELETE) } returns
          listOf(change)
        coEvery { pendingChanges("kavita:7", HighlightSyncState.PENDING_UPSERT) } returns
          emptyList()
      }
    val provider =
      RecordingProvider(mutableListOf()) {
        ProviderResult.Failure(com.charliesbot.kanshu.core.provider.ProviderError.Network)
      }

    coordinator(provider, annotations).synchronize(
      BookId("kavita:7"),
      File("book.epub"),
      mockk<Publication>(),
    ) {
      null
    }

    coVerify(exactly = 0) { annotations.acknowledgeDelete(any(), any()) }
    coVerify { annotations.applySnapshot("kavita:7", any()) }
  }

  private fun coordinator(
    provider: Provider,
    annotations: AnnotationRepository,
  ): AnnotationSyncCoordinator {
    val books =
      mockk<BookDao> {
        coEvery { find("kavita:7") } returns
          BookEntity(
            id = "kavita:7",
            providerInstanceId = "kavita",
            providerItemId = "7",
            title = "Book",
            localPath = "book.epub",
            byteSize = 1L,
            downloadedAt = 1L,
            lastOpenedAt = null,
          )
      }
    return AnnotationSyncCoordinatorImpl(
      providers = ProviderRegistryImpl(listOf(provider)),
      books = books,
      annotations = annotations,
    )
  }
}

private class RecordingProvider(
  private val events: MutableList<String>,
  private val pushResult: (HighlightChange) -> ProviderResult<HighlightPushAck>,
) : Provider {
  override val descriptor =
    ProviderDescriptor(
      id = ProviderInstanceId("kavita"),
      type = ProviderType.KAVITA,
      displayName = "Kavita",
      enabled = true,
      capabilities = ProviderCapabilities(progressSync = true, highlightSync = true),
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
  ): ProviderResult<AcquiredBook> = ProviderResult.Success(AcquiredBook(0))

  override suspend fun pushHighlight(
    context: ProviderHighlightContext,
    change: HighlightChange,
  ): ProviderResult<HighlightPushAck> {
    events +=
      when (change) {
        is HighlightChange.Delete -> "push-delete"
        is HighlightChange.Upsert -> "push-upsert"
      }
    return pushResult(change)
  }

  override suspend fun pullHighlights(
    context: ProviderHighlightContext
  ): ProviderResult<ProviderHighlightSnapshot> {
    events += "pull"
    return ProviderResult.Success(ProviderHighlightSnapshot(emptySet(), emptyList()))
  }
}
