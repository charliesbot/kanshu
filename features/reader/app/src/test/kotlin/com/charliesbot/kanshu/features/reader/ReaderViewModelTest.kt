package com.charliesbot.kanshu.features.reader

import androidx.lifecycle.ViewModelStore
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.ReaderSource
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.model.TextLeaf
import io.mockk.coEvery
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.resource.Resource
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReaderViewModelTest {
  private val testDispatcher = StandardTestDispatcher()

  @Before fun setUp() = Dispatchers.setMain(testDispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  @Test
  fun `successful open transitions to Reading`() =
    runTest(testDispatcher) {
      val publication = testPublication()
      val viewModel = viewModel(FakeReaderSource(1 to publication))

      viewModel.open(1)
      advanceUntilIdle()

      val state = viewModel.uiState.value
      assertTrue(state is ReaderUiState.Reading)
      assertEquals(
        listOf("Hello ".repeat(10).trim()),
        (state as ReaderUiState.Reading).document.paragraphText(),
      )
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
  fun `skips empty spine item and reads first chapter with text`() =
    runTest(testDispatcher) {
      val coverLink =
        mockk<Link>(relaxed = true) { every { href } returns Href("OEBPS/xhtml/cover.xhtml")!! }
      val chapterLink =
        mockk<Link>(relaxed = true) { every { href } returns Href("OEBPS/xhtml/chapter01.xhtml")!! }
      val coverResource =
        mockk<Resource> {
          coEvery { read() } returns
            Try.success(
              "<html><body><p><img alt=\"Cover\" src=\"cover.jpg\"/></p></body></html>"
                .encodeToByteArray()
            )
        }
      val chapterResource =
        mockk<Resource> {
          coEvery { read() } returns
            Try.success(
              "<html><body><p>${"Word ".repeat(30)}</p></body></html>".encodeToByteArray()
            )
        }
      val publication =
        mockk<Publication>(relaxUnitFun = true) {
          every { readingOrder } returns listOf(coverLink, chapterLink)
          every { get(coverLink) } returns coverResource
          every { get(chapterLink) } returns chapterResource
        }
      val viewModel = viewModel(FakeReaderSource(1 to publication))

      viewModel.open(1)
      advanceUntilIdle()

      val state = viewModel.uiState.value
      assertTrue(state is ReaderUiState.Reading)
      assertEquals(
        listOf("Word ".repeat(30).trim()),
        (state as ReaderUiState.Reading).document.paragraphText(),
      )
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

      assertTrue(viewModel.uiState.value is ReaderUiState.Reading)
    }

  private fun viewModel(source: ReaderSource): ReaderViewModel =
    ReaderViewModel(OpenBookUseCase(source), ioDispatcher = testDispatcher)

  private fun testPublication(): Publication {
    val resource =
      mockk<Resource> {
        coEvery { read() } returns
          Try.success("<html><body><p>${"Hello ".repeat(10)}</p></body></html>".encodeToByteArray())
      }
    val link = mockk<Link>(relaxed = true)
    return mockk(relaxUnitFun = true) {
      every { readingOrder } returns listOf(link)
      every { get(link) } returns resource
    }
  }

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

private fun ReaderDocument.paragraphText(): List<String> =
  blocks.filterIsInstance<ParagraphBlock>().map { block ->
    block.spans.filterIsInstance<TextLeaf>().joinToString("") { it.text }.trim()
  }
