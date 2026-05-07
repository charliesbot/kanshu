package com.charliesbot.kanshu.features.library

import com.charliesbot.kanshu.core.library.LibraryItem
import com.charliesbot.kanshu.core.library.LibraryResult
import com.charliesbot.kanshu.core.library.usecase.LoadLibraryUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val loadLibrary: LoadLibraryUseCase = mockk()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `success result becomes Loaded state`() = runTest {
    val items = listOf(LibraryItem(id = 1, title = "Dune", coverUrl = "url"))
    coEvery { loadLibrary() } returns LibraryResult.Success(items)
    val viewModel = LibraryViewModel(loadLibrary)

    advanceUntilIdle()

    assertEquals(LibraryUiState.Loaded(items), viewModel.uiState.value)
  }

  @Test
  fun `empty result becomes Empty state`() = runTest {
    coEvery { loadLibrary() } returns LibraryResult.Empty
    val viewModel = LibraryViewModel(loadLibrary)

    advanceUntilIdle()

    assertEquals(LibraryUiState.Empty, viewModel.uiState.value)
  }

  @Test
  fun `unauthorized error becomes Unauthorized state`() = runTest {
    coEvery { loadLibrary() } returns LibraryResult.Error.Unauthorized
    val viewModel = LibraryViewModel(loadLibrary)

    advanceUntilIdle()

    assertEquals(LibraryUiState.Error.Unauthorized, viewModel.uiState.value)
  }

  @Test
  fun `no credentials result becomes NoCredentials state`() = runTest {
    coEvery { loadLibrary() } returns LibraryResult.NoCredentials
    val viewModel = LibraryViewModel(loadLibrary)

    advanceUntilIdle()

    assertEquals(LibraryUiState.NoCredentials, viewModel.uiState.value)
  }

  @Test
  fun `network error becomes Network state`() = runTest {
    coEvery { loadLibrary() } returns LibraryResult.Error.Network
    val viewModel = LibraryViewModel(loadLibrary)

    advanceUntilIdle()

    assertEquals(LibraryUiState.Error.Network, viewModel.uiState.value)
  }

  @Test
  fun `unexpected response error becomes UnexpectedResponse state`() = runTest {
    coEvery { loadLibrary() } returns LibraryResult.Error.UnexpectedResponse
    val viewModel = LibraryViewModel(loadLibrary)

    advanceUntilIdle()

    assertEquals(LibraryUiState.Error.UnexpectedResponse, viewModel.uiState.value)
  }

  @Test
  fun `unknown error becomes Unknown state`() = runTest {
    coEvery { loadLibrary() } returns LibraryResult.Error.Unknown
    val viewModel = LibraryViewModel(loadLibrary)

    advanceUntilIdle()

    assertEquals(LibraryUiState.Error.Unknown, viewModel.uiState.value)
  }

  @Test
  fun `state is Loading before the load completes`() = runTest {
    val signal = CompletableDeferred<LibraryResult>()
    coEvery { loadLibrary() } coAnswers { signal.await() }
    val viewModel = LibraryViewModel(loadLibrary)

    // Run the dispatcher up to the suspension point inside loadLibrary().
    runCurrent()

    assertEquals(LibraryUiState.Loading, viewModel.uiState.value)

    signal.complete(LibraryResult.Empty)
    advanceUntilIdle()
    assertEquals(LibraryUiState.Empty, viewModel.uiState.value)
  }
}
