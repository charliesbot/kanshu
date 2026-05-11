package com.charliesbot.kanshu.core.library

import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.connection.KavitaCredentials
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.KavitaException
import com.charliesbot.kanshu.core.kavita.dto.ChapterDto
import com.charliesbot.kanshu.core.kavita.dto.SeriesDto
import com.charliesbot.kanshu.core.kavita.dto.VolumeDto
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookRepositoryImplTest {

  private val credentials = KavitaCredentials("https://kavita.example.com/", "secret")
  private val credentialsRepository: CredentialsRepository = mockk()
  private val api: KavitaApi = mockk()
  private lateinit var booksDir: File

  @Before
  fun setUp() {
    booksDir = Files.createTempDirectory("kanshu-books-test").toFile()
    coEvery { credentialsRepository.credentials } returns flowOf(credentials)
  }

  @After
  fun tearDown() {
    booksDir.deleteRecursively()
  }

  private fun repo(scope: CoroutineScope): BookRepositoryImpl =
    BookRepositoryImpl(
      credentialsRepository = credentialsRepository,
      api = api,
      booksDir = booksDir,
      downloadScope = scope,
    )

  @Test
  fun `observeBooks overlays download state on the snapshot`() = runTest {
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(
        SeriesDto(id = 1, name = "A", coverImage = null),
        SeriesDto(id = 2, name = "B", coverImage = null),
      )
    File(booksDir, "1.epub").writeBytes(byteArrayOf(0x1))

    val repo = repo(TestScope(StandardTestDispatcher(testScheduler)))

    val result = repo.observeBooks().first() as LibraryResult.Success
    val byId = result.items.associateBy { it.id }
    assertEquals(DownloadState.Downloaded, byId.getValue(1).downloadState)
    assertEquals(DownloadState.NotDownloaded, byId.getValue(2).downloadState)
  }

  @Test
  fun `fileFor returns the file when present`() = runTest {
    val target = File(booksDir, "42.epub")
    target.writeBytes(byteArrayOf(0xA))
    val repo = repo(TestScope(StandardTestDispatcher(testScheduler)))

    assertEquals(target.absolutePath, repo.fileFor(42)?.absolutePath)
    assertNull(repo.fileFor(43))
  }

  @Test
  fun `download writes the file and ends in Downloaded`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val scope = TestScope(dispatcher)
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(SeriesDto(id = 9, name = "X", coverImage = null))
    coEvery { api.listVolumes(any(), any(), 9) } returns
      listOf(VolumeDto(id = 1, chapters = listOf(ChapterDto(id = 100))))
    val targetSlot = slot<File>()
    val progressSlot = slot<(Long, Long?) -> Unit>()
    coEvery {
      api.downloadChapter(any(), any(), 100, capture(targetSlot), capture(progressSlot))
    } coAnswers
      {
        val target = targetSlot.captured
        val progress = progressSlot.captured
        target.writeBytes(ByteArray(50))
        progress(25L, 100L)
        progress(50L, 100L)
      }

    val repo = repo(scope)
    repo.download(9)
    scope.advanceUntilIdle()

    assertTrue(File(booksDir, "9.epub").exists())
    assertFalse(File(booksDir, "9.epub.tmp").exists())
    val result = repo.observeBooks().first() as LibraryResult.Success
    assertEquals(DownloadState.Downloaded, result.items.single { it.id == 9 }.downloadState)
  }

  @Test
  fun `download failure leaves no final file and resets state`() = runTest {
    val scope = TestScope(StandardTestDispatcher(testScheduler))
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(SeriesDto(id = 9, name = "X", coverImage = null))
    coEvery { api.listVolumes(any(), any(), 9) } returns
      listOf(VolumeDto(id = 1, chapters = listOf(ChapterDto(id = 100))))
    coEvery { api.downloadChapter(any(), any(), 100, any(), any()) } throws
      KavitaException.NetworkError

    val repo = repo(scope)
    repo.download(9)
    scope.advanceUntilIdle()

    assertFalse(File(booksDir, "9.epub").exists())
    assertFalse(File(booksDir, "9.epub.tmp").exists())
    val result = repo.observeBooks().first() as LibraryResult.Success
    assertEquals(DownloadState.NotDownloaded, result.items.single { it.id == 9 }.downloadState)
  }

  @Test
  fun `download is a no-op when already downloaded`() = runTest {
    val scope = TestScope(StandardTestDispatcher(testScheduler))
    File(booksDir, "9.epub").writeBytes(byteArrayOf(0x1))
    coEvery { api.listSeries(any(), any(), any(), any()) } returns emptyList()

    val repo = repo(scope)
    repo.download(9)
    scope.advanceUntilIdle()

    // listVolumes/downloadChapter never set up — the test would fail with a missing mock if the
    // repo had actually started a download.
  }

  @Test
  fun `delete removes the file and clears state`() = runTest {
    val scope = TestScope(StandardTestDispatcher(testScheduler))
    val target = File(booksDir, "9.epub")
    target.writeBytes(byteArrayOf(0x1))
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(SeriesDto(id = 9, name = "X", coverImage = null))

    val repo = repo(scope)
    repo.delete(9)

    assertFalse(target.exists())
    val result = repo.observeBooks().first() as LibraryResult.Success
    assertEquals(DownloadState.NotDownloaded, result.items.single { it.id == 9 }.downloadState)
  }

  @Test
  fun `scan ignores tmp files`() = runTest {
    File(booksDir, "9.epub.tmp").writeBytes(byteArrayOf(0x1))
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(SeriesDto(id = 9, name = "X", coverImage = null))

    val repo = repo(TestScope(StandardTestDispatcher(testScheduler)))

    val result = repo.observeBooks().first() as LibraryResult.Success
    assertEquals(DownloadState.NotDownloaded, result.items.single { it.id == 9 }.downloadState)
  }

  @Test
  fun `coverUrl is built when the series has a cover token`() = runTest {
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(SeriesDto(id = 7, name = "Dune", coverImage = "token"))
    val repo = repo(TestScope(StandardTestDispatcher(testScheduler)))

    val result = repo.observeBooks().first() as LibraryResult.Success
    assertNotNull(result.items.single().coverUrl)
    assertTrue(result.items.single().coverUrl!!.contains("seriesId=7"))
  }

  @Test
  fun `init sweeps orphan tmp files`() = runTest {
    val orphan = File(booksDir, "1.epub.tmp")
    orphan.writeBytes(byteArrayOf(0x1))
    coEvery { api.listSeries(any(), any(), any(), any()) } returns emptyList()

    repo(TestScope(StandardTestDispatcher(testScheduler)))

    assertFalse(orphan.exists())
  }

  @Test
  fun `NoCredentials when none saved`() = runTest {
    coEvery { credentialsRepository.credentials } returns flowOf(null)
    val repo = repo(TestScope(StandardTestDispatcher(testScheduler)))

    assertEquals(LibraryResult.NoCredentials, repo.observeBooks().first())
  }
}
