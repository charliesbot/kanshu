package com.charliesbot.kanshu.features.reader

import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val openBook: OpenBookUseCase = mockk()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun fakePublication(title: String? = "A Book"): Publication =
    mockk<Publication>(relaxed = true).also {
      val metadata = mockk<Metadata>(relaxed = true)
      every { metadata.title } returns title
      every { it.metadata } returns metadata
    }

  @Test
  fun `success result becomes Ready with title and factory`() = runTest {
    val publication = fakePublication(title = "A Book")
    coEvery { openBook(any()) } returns ReaderResult.Success(publication)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state is ReaderUiState.Ready)
    state as ReaderUiState.Ready
    assertEquals("A Book", state.title)
    assertNotNull(state.factory)
  }

  @Test
  fun `parse failed result becomes ParseFailed state`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Error.ParseFailed
    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    assertEquals(ReaderUiState.Error.ParseFailed, viewModel.uiState.value)
  }

  @Test
  fun `read failed result becomes ReadFailed state`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Error.ReadFailed
    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    assertEquals(ReaderUiState.Error.ReadFailed, viewModel.uiState.value)
  }

  @Test
  fun `not found result becomes NotFound state`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Error.NotFound
    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    assertEquals(ReaderUiState.Error.NotFound, viewModel.uiState.value)
  }
}
