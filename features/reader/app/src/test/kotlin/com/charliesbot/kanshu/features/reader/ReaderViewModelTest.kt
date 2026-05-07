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

  private fun fakeHandle(
    title: String? = null,
    chapters: Map<Int, ByteArray?> = mapOf(0 to "<p>only</p>".toByteArray()),
  ): BookHandle =
    mockk<BookHandle>(relaxed = true).also {
      every { it.title } returns title
      every { it.chapterCount } returns chapters.size
      chapters.forEach { (i, bytes) -> coEvery { it.chapterBytes(i) } returns bytes }
    }

  @Test
  fun `success result loads first chapter`() = runTest {
    val handle =
      fakeHandle(
        title = "A Book",
        chapters = mapOf(0 to "<p>chapter zero</p>".toByteArray(), 1 to "<p>one</p>".toByteArray()),
      )
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state is ReaderUiState.Ready)
    state as ReaderUiState.Ready
    assertEquals(0, state.currentIndex)
    assertEquals(2, state.chapterCount)
    assertEquals("A Book", state.title)
    assertTrue(state.currentHtml.contains("chapter zero"))
  }

  @Test
  fun `next advances chapter index`() = runTest {
    val handle =
      fakeHandle(
        chapters = mapOf(0 to "<p>zero</p>".toByteArray(), 1 to "<p>one</p>".toByteArray())
      )
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    viewModel.goNext()
    advanceUntilIdle()

    val state = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(1, state.currentIndex)
    assertTrue(state.currentHtml.contains("one"))
  }

  @Test
  fun `next at last chapter is a no-op`() = runTest {
    val handle = fakeHandle(chapters = mapOf(0 to "<p>only</p>".toByteArray()))
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    viewModel.goNext()
    advanceUntilIdle()

    val state = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(0, state.currentIndex)
  }

  @Test
  fun `prev at first chapter is a no-op`() = runTest {
    val handle = fakeHandle(chapters = mapOf(0 to "<p>only</p>".toByteArray()))
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    viewModel.goPrev()
    advanceUntilIdle()

    val state = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(0, state.currentIndex)
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

  @Test
  fun `chapter read failure becomes ReadFailed state`() = runTest {
    val handle = fakeHandle(chapters = mapOf(0 to null))
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()

    assertEquals(ReaderUiState.Error.ReadFailed, viewModel.uiState.value)
  }
}
