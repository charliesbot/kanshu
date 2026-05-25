package com.charliesbot.kanshu.features.reader

import androidx.lifecycle.ViewModelStore
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.ReaderSource
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.resource.Resource
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReaderViewModelTest {
  private val testDispatcher = StandardTestDispatcher()

  @Before fun setUp() = Dispatchers.setMain(testDispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

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
  fun `cancelling an in-flight open closes its publication`() =
    runTest(testDispatcher) {
      val delayedResource = BlockingResource()
      val first = testPublication(resource = delayedResource)
      val second = testPublication()
      val viewModel = viewModel(FakeReaderSource(1 to first, 2 to second))

      viewModel.open(1)
      advanceUntilIdle()
      assertTrue(delayedResource.readStarted.isCompleted)

      viewModel.open(2)
      advanceUntilIdle()

      verify(exactly = 1) { first.close() }
      verify(exactly = 0) { second.close() }
      assertFalse(viewModel.uiState.value is ReaderUiState.Loading)
    }

  @Test
  fun `opens first non-empty reading order resource`() =
    runTest(testDispatcher) {
      val publication =
        testPublicationWithResources(
          listOf(
            ChapterFixture(
              href = "cover.xhtml",
              resource =
                testResource("<html><body><img src='cover.jpg'></body></html>".toByteArray()),
            ),
            ChapterFixture(
              href = "chapter.xhtml",
              resource = testResource("<html><body><p>Chapter text</p></body></html>".toByteArray()),
            ),
          )
        )
      val viewModel = viewModel(FakeReaderSource(1 to publication))

      viewModel.open(1)
      advanceUntilIdle()

      val state = viewModel.uiState.value as ReaderUiState.Ready
      assertEquals("chapter.xhtml", state.href)
      assertTrue(state.chapterHtml.contains("Chapter text"))
    }

  private fun viewModel(source: ReaderSource): ReaderViewModel =
    ReaderViewModel(OpenBookUseCase(source), ioDispatcher = testDispatcher)

  private fun testPublication(resource: Resource = testResource()): Publication =
    testPublicationWithResources(
      listOf(ChapterFixture(href = "chapter.xhtml", resource = resource))
    )

  private fun testPublicationWithResources(chapters: List<ChapterFixture>): Publication {
    val links = chapters.map { chapter -> Link(Href(chapter.href) ?: error("Invalid test href")) }
    return mockk(relaxUnitFun = true) {
      every { readingOrder } returns links
      links.forEachIndexed { index, link -> every { get(link) } returns chapters[index].resource }
    }
  }

  private fun ReaderViewModel.closeThroughStore() {
    ViewModelStore().apply {
      put("reader", this@closeThroughStore)
      clear()
    }
  }
}

private data class ChapterFixture(val href: String, val resource: Resource)

private class FakeReaderSource(vararg publications: Pair<Int, Publication>) : ReaderSource {
  private val publications = publications.toMap()

  override suspend fun openBook(seriesId: Int): ReaderResult =
    ReaderResult.Success(publications.getValue(seriesId), File("test.epub"))
}

private fun testResource(
  bytes: ByteArray = "<html><body><p>Hi</p></body></html>".toByteArray()
): Resource = mockk { coEvery { read() } returns Try.success(bytes) }

private class BlockingResource : Resource {
  val readStarted = CompletableDeferred<Unit>()

  override val sourceUrl = null

  override suspend fun properties(): Try<Resource.Properties, ReadError> =
    Try.success(Resource.Properties())

  override suspend fun length(): Try<Long, ReadError> = Try.success(0L)

  override suspend fun read(range: LongRange?): Try<ByteArray, ReadError> {
    readStarted.complete(Unit)
    CompletableDeferred<Unit>().await()
    return Try.success(ByteArray(0))
  }

  override fun close() = Unit
}
