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
  fun `success result loads first chapter at page zero with unknown page count`() = runTest {
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
    assertEquals(0, state.currentChapterIndex)
    assertEquals(2, state.chapterCount)
    assertEquals(0, state.currentPageIndex)
    assertEquals(null, state.pageCount)
    assertEquals("A Book", state.title)
    assertTrue(state.currentHtml.contains("chapter zero"))
  }

  @Test
  fun `page count reported lands on page zero for fresh chapter`() = runTest {
    val handle = fakeHandle(chapters = mapOf(0 to "<p>x</p>".toByteArray()))
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    viewModel.onPageCountReported(5)
    advanceUntilIdle()

    val state = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(5, state.pageCount)
    assertEquals(0, state.currentPageIndex)
  }

  @Test
  fun `next within chapter advances page index without reloading`() = runTest {
    val handle = fakeHandle(chapters = mapOf(0 to "<p>x</p>".toByteArray()))
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    viewModel.onPageCountReported(3)
    advanceUntilIdle()

    viewModel.goNext()
    val state = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(1, state.currentPageIndex)
    assertEquals(0, state.currentChapterIndex)
  }

  @Test
  fun `prev within chapter rewinds page index`() = runTest {
    val handle = fakeHandle(chapters = mapOf(0 to "<p>x</p>".toByteArray()))
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    viewModel.onPageCountReported(3)
    advanceUntilIdle()
    viewModel.goNext()
    viewModel.goNext()

    viewModel.goPrev()
    val state = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(1, state.currentPageIndex)
  }

  @Test
  fun `next at last page rolls to next chapter at page zero`() = runTest {
    val handle =
      fakeHandle(
        chapters = mapOf(0 to "<p>zero</p>".toByteArray(), 1 to "<p>one</p>".toByteArray())
      )
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    viewModel.onPageCountReported(2)
    advanceUntilIdle()
    viewModel.goNext() // page 0 -> 1
    viewModel.goNext() // last page -> next chapter
    advanceUntilIdle()

    val midState = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(1, midState.currentChapterIndex)
    assertEquals(0, midState.currentPageIndex)
    assertEquals(null, midState.pageCount)
    assertTrue(midState.currentHtml.contains("one"))

    viewModel.onPageCountReported(4)
    advanceUntilIdle()
    val state = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(0, state.currentPageIndex)
    assertEquals(4, state.pageCount)
  }

  @Test
  fun `prev at page zero rolls to previous chapter at last page`() = runTest {
    val handle =
      fakeHandle(
        chapters = mapOf(0 to "<p>zero</p>".toByteArray(), 1 to "<p>one</p>".toByteArray())
      )
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    viewModel.onPageCountReported(1)
    viewModel.goNext() // chapter 1, page 0
    advanceUntilIdle()
    viewModel.onPageCountReported(2)
    advanceUntilIdle()

    viewModel.goPrev() // page 0 -> previous chapter, last page
    advanceUntilIdle()

    val midState = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(0, midState.currentChapterIndex)
    assertEquals(null, midState.pageCount)
    assertTrue(midState.currentHtml.contains("zero"))

    viewModel.onPageCountReported(3)
    advanceUntilIdle()
    val state = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(2, state.currentPageIndex) // last page = pageCount - 1
    assertEquals(3, state.pageCount)
  }

  @Test
  fun `next at end of last chapter is a no-op`() = runTest {
    val handle = fakeHandle(chapters = mapOf(0 to "<p>only</p>".toByteArray()))
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    viewModel.onPageCountReported(1)
    advanceUntilIdle()
    viewModel.goNext()

    val state = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(0, state.currentPageIndex)
    assertEquals(0, state.currentChapterIndex)
  }

  @Test
  fun `prev at start of first chapter is a no-op`() = runTest {
    val handle = fakeHandle(chapters = mapOf(0 to "<p>only</p>".toByteArray()))
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    viewModel.onPageCountReported(1)
    advanceUntilIdle()
    viewModel.goPrev()

    val state = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(0, state.currentPageIndex)
    assertEquals(0, state.currentChapterIndex)
  }

  @Test
  fun `resize re-report clamps current page to new max`() = runTest {
    val handle = fakeHandle(chapters = mapOf(0 to "<p>x</p>".toByteArray()))
    coEvery { openBook(any()) } returns ReaderResult.Success(handle)

    val viewModel = ReaderViewModel(seriesId = 1, openBook = openBook)
    advanceUntilIdle()
    viewModel.onPageCountReported(8)
    advanceUntilIdle()
    viewModel.goNext()
    viewModel.goNext()
    viewModel.goNext() // page 3

    viewModel.onPageCountReported(2) // shrunk; clamp to last
    advanceUntilIdle()

    val state = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(1, state.currentPageIndex)
    assertEquals(2, state.pageCount)
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
