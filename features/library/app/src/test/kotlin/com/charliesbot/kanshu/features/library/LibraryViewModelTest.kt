package com.charliesbot.kanshu.features.library

import com.charliesbot.kanshu.core.library.DownloadState
import com.charliesbot.kanshu.core.library.LibraryItem
import com.charliesbot.kanshu.core.library.LibraryResult
import com.charliesbot.kanshu.core.library.usecase.DeleteDownloadUseCase
import com.charliesbot.kanshu.core.library.usecase.DownloadBookUseCase
import com.charliesbot.kanshu.core.library.usecase.LoadLibraryUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val loadLibrary: LoadLibraryUseCase = mockk()
  private val downloadBook: DownloadBookUseCase = mockk(relaxed = true)
  private val deleteDownload: DeleteDownloadUseCase = mockk(relaxed = true)

  @Before fun setUp() = Dispatchers.setMain(testDispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  private fun viewModel(): LibraryViewModel =
    LibraryViewModel(loadLibrary, downloadBook, deleteDownload)

  @Test
  fun `success result becomes Loaded state`() = runTest {
    val items = listOf(LibraryItem(id = 1, title = "Dune", coverUrl = "url"))
    every { loadLibrary() } returns flowOf(LibraryResult.Success(items))

    val viewModel = viewModel()
    advanceUntilIdle()

    assertEquals(LibraryUiState.Loaded(items), viewModel.uiState.value)
  }

  @Test
  fun `empty result becomes Empty state`() = runTest {
    every { loadLibrary() } returns flowOf(LibraryResult.Empty)
    val viewModel = viewModel()
    advanceUntilIdle()
    assertEquals(LibraryUiState.Empty, viewModel.uiState.value)
  }

  @Test
  fun `no credentials result becomes NoCredentials state`() = runTest {
    every { loadLibrary() } returns flowOf(LibraryResult.NoCredentials)
    val viewModel = viewModel()
    advanceUntilIdle()
    assertEquals(LibraryUiState.NoCredentials, viewModel.uiState.value)
  }

  @Test
  fun `error results become Error states`() = runTest {
    every { loadLibrary() } returns flowOf(LibraryResult.Error.Unauthorized)
    var viewModel = viewModel()
    advanceUntilIdle()
    assertEquals(LibraryUiState.Error.Unauthorized, viewModel.uiState.value)

    every { loadLibrary() } returns flowOf(LibraryResult.Error.Network)
    viewModel = viewModel()
    advanceUntilIdle()
    assertEquals(LibraryUiState.Error.Network, viewModel.uiState.value)

    every { loadLibrary() } returns flowOf(LibraryResult.Error.UnexpectedResponse)
    viewModel = viewModel()
    advanceUntilIdle()
    assertEquals(LibraryUiState.Error.UnexpectedResponse, viewModel.uiState.value)

    every { loadLibrary() } returns flowOf(LibraryResult.Error.Unknown)
    viewModel = viewModel()
    advanceUntilIdle()
    assertEquals(LibraryUiState.Error.Unknown, viewModel.uiState.value)
  }

  @Test
  fun `state is Loading before the load emits`() = runTest {
    every { loadLibrary() } returns MutableSharedFlow()
    val viewModel = viewModel()
    advanceUntilIdle()
    assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `state updates when the use case emits subsequent values`() = runTest {
    val flow = MutableSharedFlow<LibraryResult>(extraBufferCapacity = 4)
    every { loadLibrary() } returns flow
    val viewModel = viewModel()
    advanceUntilIdle()

    flow.tryEmit(LibraryResult.Empty)
    advanceUntilIdle()
    assertEquals(LibraryUiState.Empty, viewModel.uiState.value)

    val items = listOf(LibraryItem(1, "A", null))
    flow.tryEmit(LibraryResult.Success(items))
    advanceUntilIdle()
    assertEquals(LibraryUiState.Loaded(items), viewModel.uiState.value)
  }

  @Test
  fun `onItemTap starts a download when not downloaded`() = runTest {
    every { loadLibrary() } returns flowOf(LibraryResult.Empty)
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.onItemTap(LibraryItem(id = 5, title = "X", coverUrl = null))

    verify { downloadBook(5) }
  }

  @Test
  fun `onItemTap is a no-op while downloading`() = runTest {
    every { loadLibrary() } returns flowOf(LibraryResult.Empty)
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.onItemTap(LibraryItem(5, "X", null, downloadState = DownloadState.Downloading(10)))

    verify(exactly = 0) { downloadBook(any()) }
  }

  @Test
  fun `onItemTap emits a navigate event when downloaded`() = runTest {
    every { loadLibrary() } returns flowOf(LibraryResult.Empty)
    val viewModel = viewModel()
    advanceUntilIdle()

    val collected = mutableListOf<LibraryItem>()
    val job = launch { viewModel.navigate.collect { collected += it } }
    runCurrent()

    val item = LibraryItem(5, "X", null, downloadState = DownloadState.Downloaded)
    viewModel.onItemTap(item)
    advanceUntilIdle()

    assertEquals(listOf(item), collected)
    job.cancel()
  }

  @Test
  fun `onItemLongPress opens dialog only for downloaded books`() = runTest {
    every { loadLibrary() } returns flowOf(LibraryResult.Empty)
    val viewModel = viewModel()
    advanceUntilIdle()

    viewModel.onItemLongPress(LibraryItem(1, "A", null))
    assertNull(viewModel.options.value)

    viewModel.onItemLongPress(
      LibraryItem(2, "B", null, downloadState = DownloadState.Downloading(50))
    )
    assertNull(viewModel.options.value)

    val downloaded = LibraryItem(3, "C", null, downloadState = DownloadState.Downloaded)
    viewModel.onItemLongPress(downloaded)
    assertEquals(downloaded, viewModel.options.value)
  }

  @Test
  fun `confirmDeleteDownload deletes and closes the dialog`() = runTest {
    every { loadLibrary() } returns flowOf(LibraryResult.Empty)
    val viewModel = viewModel()
    advanceUntilIdle()
    val item = LibraryItem(7, "X", null, downloadState = DownloadState.Downloaded)
    viewModel.onItemLongPress(item)

    viewModel.confirmDeleteDownload()

    verify { deleteDownload(7) }
    assertNull(viewModel.options.value)
  }

  @Test
  fun `dismissOptions closes the dialog without deleting`() = runTest {
    every { loadLibrary() } returns flowOf(LibraryResult.Empty)
    val viewModel = viewModel()
    advanceUntilIdle()
    viewModel.onItemLongPress(LibraryItem(7, "X", null, downloadState = DownloadState.Downloaded))

    viewModel.dismissOptions()

    verify(exactly = 0) { deleteDownload(any()) }
    assertNull(viewModel.options.value)
  }
}
