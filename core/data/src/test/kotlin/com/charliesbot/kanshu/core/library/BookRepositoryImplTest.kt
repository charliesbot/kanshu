package com.charliesbot.kanshu.core.library

import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.connection.KavitaCredentials
import com.charliesbot.kanshu.core.database.entity.BookEntity
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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
  private lateinit var bookDao: FakeBookDao

  @Before
  fun setUp() {
    booksDir = Files.createTempDirectory("kanshu-books-test").toFile()
    coEvery { credentialsRepository.credentials } returns flowOf(credentials)
    bookDao = FakeBookDao()
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
      bookDao = bookDao,
      downloadScope = scope,
    )

  private fun item(id: Int, title: String = "Title"): LibraryItem =
    LibraryItem(id = id, title = title, coverUrl = null)

  // Seeds a downloaded row in the fake DAO plus a real file on disk, the way a successful
  // download would have left things. Tests use this to set up "already downloaded" state.
  private fun seedDownloaded(seriesId: Int, title: String = "Title"): File {
    val file = File(booksDir, "$seriesId.epub")
    file.writeBytes(byteArrayOf(0x1))
    bookDao =
      FakeBookDao(
        initial =
          mapOf(
            "kavita:$seriesId" to
              BookEntity(
                id = "kavita:$seriesId",
                source = "kavita",
                sourceItemId = seriesId.toString(),
                title = title,
                localPath = file.absolutePath,
                byteSize = file.length(),
                downloadedAt = 1000L,
                lastOpenedAt = null,
              )
          )
      )
    return file
  }

  @Test
  fun `observeBooks overlays download state on the snapshot`() = runTest {
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(
        SeriesDto(id = 1, name = "A", coverImage = null),
        SeriesDto(id = 2, name = "B", coverImage = null),
      )
    seedDownloaded(1, "A")

    val repo = repo(TestScope(StandardTestDispatcher(testScheduler)))

    val result = repo.observeBooks().first() as LibraryResult.Success
    val byId = result.items.associateBy { it.id }
    assertEquals(DownloadState.Downloaded, byId.getValue(1).downloadState)
    assertEquals(DownloadState.NotDownloaded, byId.getValue(2).downloadState)
  }

  @Test
  fun `fileFor returns the file when DAO and disk agree`() = runTest {
    val target = seedDownloaded(42)
    val repo = repo(TestScope(StandardTestDispatcher(testScheduler)))

    assertEquals(target.absolutePath, repo.fileFor(42)?.absolutePath)
    assertNull(repo.fileFor(43))
  }

  @Test
  fun `fileFor returns null when DAO points at a missing file`() = runTest {
    // DAO row claims a download but the file is gone (e.g., user-side wipe between sessions).
    bookDao =
      FakeBookDao(
        initial =
          mapOf(
            "kavita:42" to
              BookEntity(
                id = "kavita:42",
                source = "kavita",
                sourceItemId = "42",
                title = "Title",
                localPath = "/nonexistent/42.epub",
                byteSize = 1L,
                downloadedAt = 1000L,
                lastOpenedAt = null,
              )
          )
      )
    val repo = repo(TestScope(StandardTestDispatcher(testScheduler)))
    assertNull(repo.fileFor(42))
  }

  @Test
  fun `download writes the file and inserts a books row`() = runTest {
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
    repo.download(item(9, "X"))
    scope.advanceUntilIdle()

    val finalFile = File(booksDir, "9.epub")
    assertTrue(finalFile.exists())
    assertFalse(File(booksDir, "9.epub.tmp").exists())
    val row = bookDao.snapshot()["kavita:9"]
    assertNotNull(row)
    assertEquals(finalFile.absolutePath, row?.localPath)
    assertEquals("X", row?.title)
    val result = repo.observeBooks().first() as LibraryResult.Success
    assertEquals(DownloadState.Downloaded, result.items.single { it.id == 9 }.downloadState)
  }

  @Test
  fun `download failure inserts no row and resets state`() = runTest {
    val scope = TestScope(StandardTestDispatcher(testScheduler))
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(SeriesDto(id = 9, name = "X", coverImage = null))
    coEvery { api.listVolumes(any(), any(), 9) } returns
      listOf(VolumeDto(id = 1, chapters = listOf(ChapterDto(id = 100))))
    coEvery { api.downloadChapter(any(), any(), 100, any(), any()) } throws
      KavitaException.NetworkError

    val repo = repo(scope)
    repo.download(item(9))
    scope.advanceUntilIdle()

    assertFalse(File(booksDir, "9.epub").exists())
    assertFalse(File(booksDir, "9.epub.tmp").exists())
    assertNull(bookDao.snapshot()["kavita:9"])
    val result = repo.observeBooks().first() as LibraryResult.Success
    assertEquals(DownloadState.NotDownloaded, result.items.single { it.id == 9 }.downloadState)
  }

  @Test
  fun `download is a no-op when already downloaded`() = runTest {
    val scope = TestScope(StandardTestDispatcher(testScheduler))
    seedDownloaded(9)
    coEvery { api.listSeries(any(), any(), any(), any()) } returns emptyList()

    val repo = repo(scope)
    repo.download(item(9))
    scope.advanceUntilIdle()

    // listVolumes/downloadChapter never set up — the test would fail with a missing mock if the
    // repo had actually started a download.
  }

  @Test
  fun `delete removes the file and clears the row`() = runTest {
    val scope = TestScope(StandardTestDispatcher(testScheduler))
    val target = seedDownloaded(9)
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(SeriesDto(id = 9, name = "X", coverImage = null))

    val repo = repo(scope)
    repo.delete(9)
    scope.advanceUntilIdle()

    assertFalse(target.exists())
    val row = bookDao.snapshot()["kavita:9"]
    // Row is kept (reading state may still be valuable) but its download metadata is null.
    assertNotNull(row)
    assertNull(row?.localPath)
    val result = repo.observeBooks().first() as LibraryResult.Success
    assertEquals(DownloadState.NotDownloaded, result.items.single { it.id == 9 }.downloadState)
  }

  @Test
  fun `reconciliation nulls localPath when the file is missing`() = runTest {
    bookDao =
      FakeBookDao(
        initial =
          mapOf(
            "kavita:7" to
              BookEntity(
                id = "kavita:7",
                source = "kavita",
                sourceItemId = "7",
                title = "Ghost",
                localPath = File(booksDir, "ghost.epub").absolutePath,
                byteSize = 1L,
                downloadedAt = 1000L,
                lastOpenedAt = null,
              )
          )
      )
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(SeriesDto(id = 7, name = "Ghost", coverImage = null))

    val scope = TestScope(StandardTestDispatcher(testScheduler))
    val repo = repo(scope)
    scope.advanceUntilIdle() // let reconcileDownloads run

    assertNull(bookDao.snapshot()["kavita:7"]?.localPath)
    val result = repo.observeBooks().first() as LibraryResult.Success
    assertEquals(DownloadState.NotDownloaded, result.items.single { it.id == 7 }.downloadState)
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

  @Test
  fun `observeBooks offline fallback serving cache`() = runTest {
    // Seed database cache with books
    bookDao.upsert(bookEntity(id = "kavita:1", title = "Cached Book 1", coverToken = "cover-1"))

    // Make network call throw exception
    coEvery { api.listSeries(any(), any(), any(), any()) } throws KavitaException.NetworkError

    val repo = repo(TestScope(StandardTestDispatcher(testScheduler)))
    val result = repo.observeBooks().first() as LibraryResult.Success
    assertEquals(1, result.items.size)
    assertEquals("Cached Book 1", result.items.single().title)
  }

  @Test
  fun `observeBooks offline empty db propagates error`() = runTest {
    // Make network call throw exception
    coEvery { api.listSeries(any(), any(), any(), any()) } throws KavitaException.NetworkError

    val repo = repo(TestScope(StandardTestDispatcher(testScheduler)))
    val result = repo.observeBooks().first()
    assertEquals(LibraryResult.Error.Network, result)
  }

  @Test
  fun `observeBooks prunes remote-deleted books only if not downloaded`() = runTest {
    // Seed one downloaded book and one non-downloaded book
    val downloadedFile = File(booksDir, "1.epub")
    downloadedFile.writeBytes(byteArrayOf(0x1))
    bookDao.upsert(
      bookEntity(
        id = "kavita:1",
        title = "Downloaded Book",
        localPath = downloadedFile.absolutePath,
      )
    )
    bookDao.upsert(bookEntity(id = "kavita:2", title = "Not Downloaded Book"))

    // Remote returns empty list (both books deleted on remote)
    coEvery { api.listSeries(any(), any(), any(), any()) } returns emptyList()

    val repo = repo(TestScope(StandardTestDispatcher(testScheduler)))
    val result = repo.observeBooks().first() as LibraryResult.Success

    // Not Downloaded Book (2) is deleted, Downloaded Book (1) is preserved.
    assertEquals(1, result.items.size)
    assertEquals(1, result.items.single().id)
    assertNotNull(bookDao.snapshot()["kavita:1"])
    assertNull(bookDao.snapshot()["kavita:2"])
  }

  @Test
  fun `observeBooks reactive flow emits updates when database state changes`() = runTest {
    coEvery { api.listSeries(any(), any(), any(), any()) } returns
      listOf(SeriesDto(id = 1, name = "Book 1", coverImage = null))

    val repo = repo(TestScope(StandardTestDispatcher(testScheduler)))

    // Launch a collector that grabs two emissions
    val emissions = mutableListOf<LibraryResult>()
    val job = launch { repo.observeBooks().take(2).toList(emissions) }

    testScheduler.runCurrent()

    // First emission should be Success with DownloadState.NotDownloaded
    val firstSuccess = emissions.first() as LibraryResult.Success
    assertEquals(1, firstSuccess.items.size)
    assertEquals(DownloadState.NotDownloaded, firstSuccess.items.single().downloadState)

    // Simulate db update
    bookDao.upsert(bookEntity(id = "kavita:1", title = "Book 1", localPath = "/path/to/book.epub"))
    testScheduler.runCurrent()

    // Second emission should be Success with DownloadState.Downloaded
    val secondSuccess = emissions.last() as LibraryResult.Success
    assertEquals(1, secondSuccess.items.size)
    assertEquals(DownloadState.Downloaded, secondSuccess.items.single().downloadState)

    job.cancel()
  }

  private fun bookEntity(
    id: String,
    title: String,
    localPath: String? = null,
    coverToken: String? = null,
  ) =
    BookEntity(
      id = id,
      source = "kavita",
      sourceItemId = id.removePrefix("kavita:"),
      title = title,
      localPath = localPath,
      byteSize = if (localPath != null) 100L else null,
      downloadedAt = if (localPath != null) 1000L else null,
      lastOpenedAt = null,
      coverToken = coverToken,
    )
}
