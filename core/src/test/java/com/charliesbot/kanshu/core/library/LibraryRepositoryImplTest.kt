package com.charliesbot.kanshu.core.library

import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.connection.KavitaCredentials
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaException
import com.charliesbot.kanshu.core.kavita.dto.SeriesDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRepositoryImplTest {

  private val credentialsRepository: CredentialsRepository = mockk()
  private val api: KavitaApi = mockk()
  private val repository = LibraryRepositoryImpl(credentialsRepository, api)

  @Test
  fun `returns NoCredentials when none saved`() = runTest {
    coEvery { credentialsRepository.credentials } returns flowOf(null)

    val result = repository.loadLibrary()

    assertEquals(LibraryResult.NoCredentials, result)
  }

  @Test
  fun `maps series to library items with built cover URLs`() = runTest {
    coEvery { credentialsRepository.credentials } returns
      flowOf(KavitaCredentials("https://kavita.example.com/", "secret"))
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(SeriesDto(id = 7, name = "Dune", coverImage = "token"))

    val result = repository.loadLibrary()

    assertTrue(result is LibraryResult.Success)
    val item = (result as LibraryResult.Success).items.single()
    assertEquals(7, item.id)
    assertEquals("Dune", item.title)
    assertEquals(
      "https://kavita.example.com/api/Image/series-cover?seriesId=7&apiKey=secret",
      item.coverUrl,
    )
  }

  @Test
  fun `returns Empty when api returns no series`() = runTest {
    coEvery { credentialsRepository.credentials } returns
      flowOf(KavitaCredentials("https://kavita.example.com", "secret"))
    coEvery { api.listSeries(any(), any(), any(), any()) } returns emptyList()

    val result = repository.loadLibrary()

    assertEquals(LibraryResult.Empty, result)
  }

  @Test
  fun `maps Unauthorized exception to Unauthorized error`() = runTest {
    coEvery { credentialsRepository.credentials } returns
      flowOf(KavitaCredentials("https://kavita.example.com", "secret"))
    coEvery { api.listSeries(any(), any(), any(), any()) } throws KavitaException.Unauthorized

    val result = repository.loadLibrary()

    assertEquals(LibraryResult.Error.Unauthorized, result)
  }

  @Test
  fun `maps NetworkError exception to Network error`() = runTest {
    coEvery { credentialsRepository.credentials } returns
      flowOf(KavitaCredentials("https://kavita.example.com", "secret"))
    coEvery { api.listSeries(any(), any(), any(), any()) } throws KavitaException.NetworkError

    val result = repository.loadLibrary()

    assertEquals(LibraryResult.Error.Network, result)
  }

  @Test
  fun `coverUrl is null when series has no cover image token`() = runTest {
    coEvery { credentialsRepository.credentials } returns
      flowOf(KavitaCredentials("https://kavita.example.com", "secret"))
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(SeriesDto(id = 7, name = "Dune", coverImage = null))

    val result = repository.loadLibrary()

    val item = (result as LibraryResult.Success).items.single()
    assertNull(item.coverUrl)
  }

  @Test
  fun `coverUrl encodes api key with reserved characters`() = runTest {
    coEvery { credentialsRepository.credentials } returns
      flowOf(KavitaCredentials("https://kavita.example.com", "a&b=c+d"))
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(SeriesDto(id = 7, name = "Dune", coverImage = "token"))

    val result = repository.loadLibrary()

    val item = (result as LibraryResult.Success).items.single()
    assertEquals(
      "https://kavita.example.com/api/Image/series-cover?seriesId=7&apiKey=a%26b%3Dc%2Bd",
      item.coverUrl,
    )
  }
}
