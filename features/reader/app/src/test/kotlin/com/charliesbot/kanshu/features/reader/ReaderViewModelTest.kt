package com.charliesbot.kanshu.features.reader

import androidx.lifecycle.ViewModelStore
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.ReaderSource
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Publication
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReaderViewModelTest {
  private val testDispatcher = StandardTestDispatcher()

  @Before fun setUp() = Dispatchers.setMain(testDispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  @Test
  fun `successful open transitions to Ready`() =
    runTest(testDispatcher) {
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()))

      viewModel.open(1)
      advanceUntilIdle()

      assertEquals(ReaderUiState.Ready, viewModel.uiState.value)
    }

  @Test
  fun `not found transitions to Error`() =
    runTest(testDispatcher) {
      val source =
        object : ReaderSource {
          override suspend fun openBook(seriesId: Int) = ReaderResult.Error.NotFound
        }
      val viewModel = viewModel(source)

      viewModel.open(1)
      advanceUntilIdle()

      assertEquals(ReaderUiState.Error.NotFound, viewModel.uiState.value)
    }

  @Test
  fun `parse failed transitions to OpenFailed`() =
    runTest(testDispatcher) {
      val source =
        object : ReaderSource {
          override suspend fun openBook(seriesId: Int) = ReaderResult.Error.ParseFailed
        }
      val viewModel = viewModel(source)

      viewModel.open(1)
      advanceUntilIdle()

      assertEquals(ReaderUiState.Error.OpenFailed, viewModel.uiState.value)
    }

  @Test
  fun `read failed transitions to OpenFailed`() =
    runTest(testDispatcher) {
      val source =
        object : ReaderSource {
          override suspend fun openBook(seriesId: Int) = ReaderResult.Error.ReadFailed
        }
      val viewModel = viewModel(source)

      viewModel.open(1)
      advanceUntilIdle()

      assertEquals(ReaderUiState.Error.OpenFailed, viewModel.uiState.value)
    }

  @Test
  fun `successful publication closes on clear`() =
    runTest(testDispatcher) {
      val publication = testPublication()
      val viewModel = viewModel(FakeReaderSource(1 to publication))

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.closeThroughStore()

      verify(exactly = 1) { publication.close() }
    }

  @Test
  fun `opening another series closes previous publication`() =
    runTest(testDispatcher) {
      val first = testPublication()
      val second = testPublication()
      val viewModel = viewModel(FakeReaderSource(1 to first, 2 to second))

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.open(2)
      advanceUntilIdle()

      verify(exactly = 1) { first.close() }
      verify(exactly = 0) { second.close() }
    }

  @Test
  fun `duplicate open with same seriesId is no-op`() =
    runTest(testDispatcher) {
      val publication = testPublication()
      val viewModel = viewModel(FakeReaderSource(1 to publication))

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.open(1)
      advanceUntilIdle()

      assertEquals(ReaderUiState.Ready, viewModel.uiState.value)
    }

  private fun viewModel(source: ReaderSource): ReaderViewModel =
    ReaderViewModel(OpenBookUseCase(source), ioDispatcher = testDispatcher)

  private fun testPublication(): Publication =
    mockk(relaxUnitFun = true) { every { readingOrder } returns emptyList() }

  private fun ReaderViewModel.closeThroughStore() {
    ViewModelStore().apply {
      put("reader", this@closeThroughStore)
      clear()
    }
  }
}

private class FakeReaderSource(vararg publications: Pair<Int, Publication>) : ReaderSource {
  private val publications = publications.toMap()

  override suspend fun openBook(seriesId: Int): ReaderResult =
    ReaderResult.Success(publications.getValue(seriesId), File("test.epub"))
}
