package com.charliesbot.kanshu.core.provider

import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.readium.r2.shared.publication.Publication

class ProviderHighlightContractTest {
  @Test
  fun defaultHighlightOperationsUseSnapshotsAndPerChangeAcknowledgements() = runTest {
    val provider = provider()
    val context =
      ProviderHighlightContext(
        book =
          ProviderBookContext(
            book = ProviderBookKey(provider.descriptor.id, "book"),
            file = File("book.epub"),
            publication = mockk<Publication>(),
          ),
        sourceMapForSpine = { null },
      )

    assertEquals(
      ProviderResult.Success(ProviderHighlightSnapshot(emptySet(), emptyList())),
      provider.pullHighlights(context),
    )
    val change = HighlightChange.Delete("local", "remote", 7L)
    assertEquals(
      ProviderResult.Success(HighlightPushAck()),
      provider.pushHighlight(context, change),
    )
  }

  private fun provider() =
    object : Provider {
      override val descriptor =
        ProviderDescriptor(
          id = ProviderInstanceId("local-only"),
          type = ProviderType.LOCAL,
          displayName = "Local only",
          enabled = true,
          capabilities = ProviderCapabilities(progressSync = false, highlightSync = false),
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
    }
}
