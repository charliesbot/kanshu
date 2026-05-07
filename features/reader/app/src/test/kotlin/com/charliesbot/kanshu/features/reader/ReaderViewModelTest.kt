package com.charliesbot.kanshu.features.reader

import com.charliesbot.kanshu.core.reader.BookHandle
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

  private fun fakeHandle(title: String? = "A Book"): BookHandle =
    mockk<BookHandle>(relaxed = true).also {
      val publication = mockk<Publication>(relaxed = true)
      val metadata = mockk<Metadata>(relaxed = true)
      every { metadata.title } returns title
      every { publication.metadata } returns metadata
      every { it.publication } returns publication
    }

  @Test
  fun `success result becomes Ready with title and factory`() = runTest {
    val handle = fakeHandle(title = "A Book")
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state is ReaderUiState.Ready)
    state as ReaderUiState.Ready
    assertEquals("A Book", state.title)
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
