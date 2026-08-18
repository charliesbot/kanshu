package com.charliesbot.kanshu.core.library

import com.charliesbot.kanshu.core.database.entity.BookEntity
import com.charliesbot.kanshu.core.provider.AcquiredBook
import com.charliesbot.kanshu.core.provider.BookId
import com.charliesbot.kanshu.core.provider.Provider
import com.charliesbot.kanshu.core.provider.ProviderBook
import com.charliesbot.kanshu.core.provider.ProviderBookKey
import com.charliesbot.kanshu.core.provider.ProviderCapabilities
import com.charliesbot.kanshu.core.provider.ProviderCover
import com.charliesbot.kanshu.core.provider.ProviderDescriptor
import com.charliesbot.kanshu.core.provider.ProviderError
import com.charliesbot.kanshu.core.provider.ProviderInstanceId
import com.charliesbot.kanshu.core.provider.ProviderRegistryImpl
import com.charliesbot.kanshu.core.provider.ProviderResult
import com.charliesbot.kanshu.core.provider.ProviderType
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookRepositoryProviderAggregationTest {
  @Test
  fun `successful provider remains visible when another provider fails`() = runTest {
    val successful =
      FakeProvider(
        id = "successful",
        catalog = ProviderResult.Success(listOf(providerBook("successful", "1", "Available"))),
      )
    val failed =
      FakeProvider(id = "failed", catalog = ProviderResult.Failure(ProviderError.Network))

    val result = repository(FakeBookDao(), successful, failed).observeBooks().first()

    assertEquals(listOf("Available"), (result as LibraryResult.Success).items.map { it.title })
  }

  @Test
  fun `successful empty provider yields empty when another provider fails`() = runTest {
    val empty = FakeProvider(id = "empty", catalog = ProviderResult.Success(emptyList()))
    val failed =
      FakeProvider(id = "failed", catalog = ProviderResult.Failure(ProviderError.Network))

    assertEquals(
      LibraryResult.Empty,
      repository(FakeBookDao(), empty, failed).observeBooks().first(),
    )
  }

  @Test
  fun `provider refresh only replaces its own catalog snapshot`() = runTest {
    val dao =
      FakeBookDao(
        mapOf(
          "other:9" to bookEntity("other:9", "other", "9", "Cached"),
          "refreshing:old" to bookEntity("refreshing:old", "refreshing", "old", "Old"),
        )
      )
    val refreshing =
      FakeProvider(
        id = "refreshing",
        catalog = ProviderResult.Success(listOf(providerBook("refreshing", "new", "New"))),
      )
    val other = FakeProvider(id = "other", catalog = ProviderResult.Failure(ProviderError.Network))

    val result = repository(dao, refreshing, other).observeBooks().first()

    assertEquals(
      setOf("Cached", "New"),
      (result as LibraryResult.Success).items.map { it.title }.toSet(),
    )
  }

  @Test
  fun `disabled provider is hidden from cached library aggregation`() = runTest {
    val dao =
      FakeBookDao(mapOf("disabled:1" to bookEntity("disabled:1", "disabled", "1", "Hidden")))
    val disabled =
      FakeProvider(
        id = "disabled",
        enabled = false,
        catalog = ProviderResult.Success(emptyList()),
      )

    assertEquals(LibraryResult.Empty, repository(dao, disabled).observeBooks().first())
  }

  @Test
  fun `download routes an arbitrary book id to its owning provider key`() = runTest {
    val provider =
      FakeProvider(
        id = "archive",
        catalog = ProviderResult.Success(emptyList()),
        acquireSucceeds = true,
      )
    val dao =
      FakeBookDao(
        mapOf("archive:item-42" to bookEntity("archive:item-42", "archive", "item-42", "Book"))
      )
    val scope = TestScope(StandardTestDispatcher(testScheduler))
    val repo = repository(dao, scope, provider)

    repo.download(LibraryItem(BookId("archive:item-42"), "Book", null))
    scope.advanceUntilIdle()

    assertEquals(
      ProviderBookKey(ProviderInstanceId("archive"), "item-42"),
      provider.acquiredBook,
    )
    assertTrue(File(dao.snapshot().getValue("archive:item-42").localPath!!).exists())
  }

  private fun repository(dao: FakeBookDao, vararg providers: Provider): BookRepositoryImpl =
    repository(dao, TestScope(StandardTestDispatcher()), *providers)

  private fun repository(
    dao: FakeBookDao,
    scope: TestScope,
    vararg providers: Provider,
  ): BookRepositoryImpl =
    BookRepositoryImpl(
      providers = ProviderRegistryImpl(providers.toList()),
      booksDir = Files.createTempDirectory("provider-aggregation").toFile(),
      bookDao = dao,
      downloadScope = scope,
    )

  private fun providerBook(providerId: String, itemId: String, title: String) =
    ProviderBook(
      key = ProviderBookKey(ProviderInstanceId(providerId), itemId),
      title = title,
      cover = null,
      mediaType = "application/epub+zip",
      revisionToken = null,
    )

  private fun bookEntity(id: String, providerId: String, itemId: String, title: String) =
    BookEntity(
      id = id,
      providerInstanceId = providerId,
      providerItemId = itemId,
      title = title,
      localPath = null,
      byteSize = null,
      downloadedAt = null,
      lastOpenedAt = null,
    )
}

private class FakeProvider(
  id: String,
  enabled: Boolean = true,
  private val catalog: ProviderResult<List<ProviderBook>>,
  private val acquireSucceeds: Boolean = false,
) : Provider {
  var acquiredBook: ProviderBookKey? = null
    private set

  override val descriptor =
    ProviderDescriptor(
      id = ProviderInstanceId(id),
      type = ProviderType.KAVITA,
      displayName = id,
      enabled = enabled,
      capabilities = ProviderCapabilities(progressSync = false, highlightSync = false),
    )

  override suspend fun fetchCatalog(): ProviderResult<List<ProviderBook>> = catalog

  override suspend fun resolveCover(
    book: ProviderBookKey,
    revisionToken: String?,
  ): ProviderCover? = null

  override suspend fun acquire(
    book: ProviderBookKey,
    target: File,
    onProgress: (downloaded: Long, total: Long?) -> Unit,
  ): ProviderResult<AcquiredBook> {
    acquiredBook = book
    if (!acquireSucceeds) return ProviderResult.Failure(ProviderError.Network)
    target.writeBytes(byteArrayOf(1))
    return ProviderResult.Success(AcquiredBook(target.length()))
  }
}
