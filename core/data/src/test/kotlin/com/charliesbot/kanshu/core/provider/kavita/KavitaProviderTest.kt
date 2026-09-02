package com.charliesbot.kanshu.core.provider.kavita

import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.connection.KavitaCredentials
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaException
import com.charliesbot.kanshu.core.kavita.dto.ChapterDto
import com.charliesbot.kanshu.core.kavita.dto.SeriesDto
import com.charliesbot.kanshu.core.kavita.dto.VolumeDto
import com.charliesbot.kanshu.core.provider.ProviderBookContext
import com.charliesbot.kanshu.core.provider.ProviderBookKey
import com.charliesbot.kanshu.core.provider.ProviderCover
import com.charliesbot.kanshu.core.provider.ProviderError
import com.charliesbot.kanshu.core.provider.ProviderResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.publication.Publication

class KavitaProviderTest {
  private val api: KavitaApi = mockk()
  private val credentials: CredentialsRepository = mockk()
  private val provider = KavitaProvider(credentials, api)

  @Test
  fun `catalog maps Kavita series to provider books`() = runTest {
    coEvery { credentials.credentials } returns
      flowOf(KavitaCredentials("https://kavita.example/", "key&value"))
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(SeriesDto(id = 42, name = "Book", coverImage = "revision", libraryId = 9))

    val result = provider.fetchCatalog() as ProviderResult.Success
    val book = result.value.single()

    assertEquals(ProviderBookKey(KavitaProvider.ID, "42"), book.key)
    assertEquals("Book", book.title)
    assertEquals("application/epub+zip", book.mediaType)
    assertEquals("revision", book.revisionToken)
    assertEquals(ProviderCover.Available, book.cover)
    assertEquals(mapOf("seriesId" to "42", "libraryId" to "9"), book.providerMetadata)
  }

  @Test
  fun `catalog reports missing credentials explicitly`() = runTest {
    coEvery { credentials.credentials } returns flowOf(null)

    assertEquals(
      ProviderResult.Failure(ProviderError.NoCredentials),
      provider.fetchCatalog(),
    )
  }

  @Test
  fun `cover resolution keeps credentials inside the provider`() = runTest {
    coEvery { credentials.credentials } returns
      flowOf(KavitaCredentials("https://kavita.example/", "key&value"))

    val cover =
      provider.resolveCover(ProviderBookKey(KavitaProvider.ID, "42"), "revision")
        as ProviderCover.RemoteUrl

    assertEquals(
      "https://kavita.example/api/Image/series-cover?seriesId=42&apiKey=key%26value",
      cover.value,
    )
  }

  @Test
  fun `acquire resolves the first deterministic chapter and writes the target`() = runTest {
    coEvery { credentials.credentials } returns
      flowOf(KavitaCredentials("https://kavita.example", "key"))
    coEvery { api.listVolumes(any(), any(), 42) } returns
      listOf(
        VolumeDto(id = 20, chapters = listOf(ChapterDto(id = 200))),
        VolumeDto(id = 10, chapters = listOf(ChapterDto(id = 100))),
      )
    val target = Files.createTempFile("kavita-provider", ".epub").toFile()
    coEvery { api.downloadChapter(any(), any(), any(), any(), any()) } coAnswers
      {
        val output = arg<File>(3)
        output.writeBytes(byteArrayOf(1, 2, 3))
      }

    val result = provider.acquire(ProviderBookKey(KavitaProvider.ID, "42"), target) { _, _ -> }

    val acquired = (result as ProviderResult.Success).value
    assertEquals(3L, acquired.byteSize)
    assertEquals("10", acquired.providerMetadata["volumeId"])
    assertEquals("100", acquired.providerMetadata["chapterId"])
    coVerify { api.downloadChapter(any(), any(), 100, target, any()) }
    target.delete()
  }

  @Test
  fun `provider maps Kavita failures without leaking wire exceptions`() = runTest {
    coEvery { credentials.credentials } returns
      flowOf(KavitaCredentials("https://kavita.example", "key"))
    coEvery { api.listSeries(any(), any(), any(), any()) } throws KavitaException.Unauthorized

    assertEquals(
      ProviderResult.Failure(ProviderError.Unauthorized),
      provider.fetchCatalog(),
    )
  }

  @Test
  fun `acquire rejects another provider's key`() = runTest {
    val failure =
      runCatching {
          provider.acquire(
            ProviderBookKey(com.charliesbot.kanshu.core.provider.ProviderInstanceId("other"), "42"),
            File("ignored"),
          ) { _, _ ->
          }
        }
        .exceptionOrNull()

    assertTrue(failure is IllegalArgumentException)
  }

  @Test
  fun `progress reports missing credentials through provider result`() = runTest {
    coEvery { credentials.credentials } returns flowOf(null)
    val context =
      ProviderBookContext(
        book = ProviderBookKey(KavitaProvider.ID, "42"),
        file = File("missing.epub"),
        publication = mockk<Publication>(),
      )

    assertEquals(
      ProviderResult.Failure(ProviderError.NoCredentials),
      provider.pullProgress(context),
    )
  }
}
