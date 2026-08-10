package com.charliesbot.kanshu.core.provider

import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.readium.r2.shared.publication.Publication

class ProviderHighlightContractTest {
  @Test
  fun `default highlight operations are successful no-ops`() = runTest {
    val provider = provider()
    val context = context(provider)

    assertEquals(
      ProviderResult.Success(emptyList<ProviderHighlight>()),
      provider.pullHighlights(context),
    )
    assertEquals(ProviderResult.Success(Unit), provider.pushHighlights(context, emptyList()))
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

  private fun context(provider: Provider) =
    ProviderBookContext(
      book = ProviderBookKey(provider.descriptor.id, "book"),
      file = File("book.epub"),
      publication = mockk<Publication>(),
    )
}
