package com.charliesbot.kanshu.features.reader

import androidx.lifecycle.ViewModelStore
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.ReaderSource
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import com.charliesbot.kanshu.navigator.model.ImageBlock
import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.ParseDiagnostics
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.model.TextLeaf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
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
  fun `successful open exposes resource loader`() =
    runTest(testDispatcher) {
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()))

      viewModel.open(1)
      advanceUntilIdle()

      assertNotNull(viewModel.resourceLoader.value)
    }

  @Test
  fun `failed open leaves resource loader null`() =
    runTest(testDispatcher) {
      val source =
        object : ReaderSource {
          override suspend fun openBook(seriesId: Int) = ReaderResult.Error.NotFound
        }
      val viewModel = viewModel(source)

      viewModel.open(1)
      advanceUntilIdle()

      assertNull(viewModel.resourceLoader.value)
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
  fun `open respects cover-like first spine item`() =
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

      assertEquals(
        listOf(ImageBlock(resourceHref = "cover.jpg", alt = "Cover")),
        viewModel.currentDocument().blocks,
      )
    }

  @Test
  fun `empty first spine item opens and next page advances to following spine item`() =
    runTest(testDispatcher) {
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to
              testPublication(
                "<html><body></body></html>",
                "<html><body><p>${"Second chapter ".repeat(6)}</p></body></html>",
              )
          )
        )

      viewModel.open(1)
      advanceUntilIdle()

      val firstDocument = viewModel.currentDocument()
      assertTrue(firstDocument.blocks.isEmpty())

      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)
      viewModel.nextPage()
      advanceUntilIdle()

      val secondState = viewModel.uiState.value
      assertTrue(secondState is ReaderUiState.Reading)
      assertEquals(
        listOf("Second chapter ".repeat(6).trim()),
        (secondState as ReaderUiState.Reading).document.paragraphText(),
      )
      assertEquals(0, viewModel.currentPage.value)
      assertEquals(0, viewModel.pageCount.value)
    }

  @Test
  fun `styled text opens as spine content`() =
    runTest(testDispatcher) {
      val publication =
        testPublication(
          "<html><body><p><strong>${"Bold text ".repeat(8)}</strong></p></body></html>"
        )
      val viewModel = viewModel(FakeReaderSource(1 to publication))

      viewModel.open(1)
      advanceUntilIdle()

      assertTrue(viewModel.uiState.value is ReaderUiState.Reading)
    }

  @Test
  fun `successful open exposes parser diagnostics`() =
    runTest(testDispatcher) {
      val publication =
        testPublication(
          """
          <html>
            <body>
              <p>Before</p>
              <table><tr><td>Cell</td></tr></table>
              <aside>Note</aside>
            </body>
          </html>
          """
            .trimIndent()
        )
      val viewModel = viewModel(FakeReaderSource(1 to publication))

      viewModel.open(1)
      advanceUntilIdle()

      assertEquals(
        ParseDiagnostics(unsupportedBlockTags = mapOf("table" to 1, "aside" to 1)),
        viewModel.currentDiagnostics(),
      )
    }

  @Test
  fun `nextPage on last page opens next spine item`() =
    runTest(testDispatcher) {
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to
              testPublication(
                "<html><body><p>${"First chapter ".repeat(6)}</p></body></html>",
                "<html><body><p>${"Second chapter ".repeat(6)}</p></body></html>",
              )
          )
        )

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)

      viewModel.nextPage()
      advanceUntilIdle()

      val state = viewModel.uiState.value
      assertTrue(state is ReaderUiState.Reading)
      assertEquals(
        listOf("Second chapter ".repeat(6).trim()),
        (state as ReaderUiState.Reading).document.paragraphText(),
      )
      assertEquals(0, viewModel.currentPage.value)
      assertEquals(0, viewModel.pageCount.value)
    }

  @Test
  fun `nextPage emits Reading state for adjacent identical spine documents`() =
    runTest(testDispatcher) {
      val imageOnly = "<html><body><p><img alt=\"image\" src=\"cover.jpg\"/></p></body></html>"
      val viewModel = viewModel(FakeReaderSource(1 to testPublication(imageOnly, imageOnly)))

      viewModel.open(1)
      advanceUntilIdle()
      assertEquals(0, viewModel.currentSpineIndex())
      assertEquals(
        listOf(ImageBlock(resourceHref = "cover.jpg", alt = "image")),
        viewModel.currentDocument().blocks,
      )
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)

      viewModel.nextPage()
      advanceUntilIdle()

      assertEquals(1, viewModel.currentSpineIndex())
      assertEquals(
        listOf(ImageBlock(resourceHref = "cover.jpg", alt = "image")),
        viewModel.currentDocument().blocks,
      )
      assertEquals(0, viewModel.currentPage.value)
      assertEquals(0, viewModel.pageCount.value)
    }

  @Test
  fun `nextPage on last page stays put when there is no next spine item`() =
    runTest(testDispatcher) {
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()))

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)

      viewModel.nextPage()
      advanceUntilIdle()

      val state = viewModel.uiState.value
      assertTrue(state is ReaderUiState.Reading)
      assertEquals(
        listOf("Hello ".repeat(10).trim()),
        (state as ReaderUiState.Reading).document.paragraphText(),
      )
      assertEquals(0, viewModel.currentPage.value)
      assertEquals(1, viewModel.pageCount.value)
    }

  @Test
  fun `nextPage does not skip unreadable next spine item`() =
    runTest(testDispatcher) {
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to
              testPublicationWithMissingResource(
                missingResourceIndex = 1,
                "<html><body><p>${"First chapter ".repeat(6)}</p></body></html>",
                "<html><body><p>${"Broken chapter ".repeat(6)}</p></body></html>",
                "<html><body><p>${"Third chapter ".repeat(6)}</p></body></html>",
              )
          )
        )

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)

      viewModel.nextPage()
      advanceUntilIdle()

      assertEquals(0, viewModel.currentSpineIndex())
      assertEquals(
        listOf("First chapter ".repeat(6).trim()),
        viewModel.currentDocument().paragraphText(),
      )
      assertEquals(0, viewModel.currentPage.value)
      assertEquals(1, viewModel.pageCount.value)
    }

  @Test
  fun `previousPage moves back within chapter`() =
    runTest(testDispatcher) {
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()))

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 3)
      viewModel.nextPage()

      viewModel.previousPage()

      assertEquals(0, viewModel.currentPage.value)
    }

  @Test
  fun `previousPage on first page opens previous spine item at its last page`() =
    runTest(testDispatcher) {
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to
              testPublication(
                "<html><body><p>${"First chapter ".repeat(6)}</p></body></html>",
                "<html><body><p>${"Second chapter ".repeat(6)}</p></body></html>",
              )
          )
        )

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)
      viewModel.nextPage()
      advanceUntilIdle()
      assertEquals(1, viewModel.currentSpineIndex())
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)

      viewModel.previousPage()
      advanceUntilIdle()

      assertEquals(0, viewModel.currentSpineIndex())
      assertEquals(
        listOf("First chapter ".repeat(6).trim()),
        viewModel.currentDocument().paragraphText(),
      )
      assertEquals(0, viewModel.pageCount.value)

      viewModel.onPageCount(viewModel.currentSpineIndex(), 4)

      assertEquals(3, viewModel.currentPage.value)
      assertEquals(4, viewModel.pageCount.value)
    }

  @Test
  fun `previousPage on first page of first spine item stays put`() =
    runTest(testDispatcher) {
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()))

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)

      viewModel.previousPage()
      advanceUntilIdle()

      assertEquals(0, viewModel.currentSpineIndex())
      assertEquals(0, viewModel.currentPage.value)
      assertEquals(1, viewModel.pageCount.value)
    }

  @Test
  fun `previousPage while page count is unknown is ignored`() =
    runTest(testDispatcher) {
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to
              testPublication(
                "<html><body><p>${"First chapter ".repeat(6)}</p></body></html>",
                "<html><body><p>${"Second chapter ".repeat(6)}</p></body></html>",
              )
          )
        )

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)
      viewModel.nextPage()
      advanceUntilIdle()
      assertEquals(1, viewModel.currentSpineIndex())

      viewModel.previousPage()
      advanceUntilIdle()

      assertEquals(1, viewModel.currentSpineIndex())
      assertEquals(0, viewModel.currentPage.value)
    }

  @Test
  fun `previousPage on first page ignores duplicate previous spine open while loading`() =
    runTest(testDispatcher) {
      val firstChapter = "<html><body><p>${"First chapter ".repeat(6)}</p></body></html>"
      val links = List(2) { mockk<Link>(relaxed = true) }
      val firstResource =
        mockk<Resource> {
          coEvery { read() } coAnswers
            {
              delay(1_000)
              Try.success(firstChapter.encodeToByteArray())
            }
        }
      val secondResource =
        mockk<Resource> {
          coEvery { read() } returns
            Try.success(
              "<html><body><p>${"Second chapter ".repeat(6)}</p></body></html>".encodeToByteArray()
            )
        }
      val publication =
        mockk<Publication>(relaxUnitFun = true) {
          every { readingOrder } returns links
          every { get(links[0]) } returns firstResource
          every { get(links[1]) } returns secondResource
        }
      val viewModel = viewModel(FakeReaderSource(1 to publication))

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)
      viewModel.nextPage()
      advanceUntilIdle()
      assertEquals(1, viewModel.currentSpineIndex())
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)

      viewModel.previousPage()
      viewModel.previousPage()
      advanceUntilIdle()

      assertEquals(0, viewModel.currentSpineIndex())
      assertEquals(
        listOf("First chapter ".repeat(6).trim()),
        viewModel.currentDocument().paragraphText(),
      )
      // The initial open is the only read; reentry is served from the spine item cache.
      coVerify(exactly = 1) { firstResource.read() }
    }

  @Test
  fun `publisher stylesheet applies emphasis and is fetched once across chapters`() =
    runTest(testDispatcher) {
      val chapter =
        """
        <html>
          <head><link rel="stylesheet" href="../styles/main.css"/></head>
          <body><p>It was <span class="calibre7">not</span> a good idea. ${"Filler ".repeat(5)}</p></body>
        </html>
        """
          .trimIndent()
      val links =
        listOf("OEBPS/xhtml/ch01.xhtml", "OEBPS/xhtml/ch02.xhtml").map { path ->
          mockk<Link>(relaxed = true) { every { href } returns Href(path)!! }
        }
      val chapterResource =
        mockk<Resource> { coEvery { read() } returns Try.success(chapter.encodeToByteArray()) }
      val cssResource =
        mockk<Resource> {
          coEvery { read() } returns
            Try.success(".calibre7 { font-style: italic }".encodeToByteArray())
        }
      val publication =
        mockk<Publication>(relaxUnitFun = true) {
          every { readingOrder } returns links
          links.forEach { link -> every { get(link) } returns chapterResource }
          every { get(any<Url>()) } answers
            {
              val url = firstArg<Url>()
              if (url.toString().endsWith("main.css")) cssResource else null
            }
        }
      val viewModel = viewModel(FakeReaderSource(1 to publication))

      viewModel.open(1)
      advanceUntilIdle()

      val spans = (viewModel.currentDocument().blocks.first() as ParagraphBlock).spans
      assertTrue(spans.any { it is TextLeaf && it.text == "not" && it.style == InlineStyle.Italic })

      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)
      viewModel.nextPage()
      advanceUntilIdle()
      assertEquals(1, viewModel.currentSpineIndex())

      coVerify(exactly = 1) { cssResource.read() }
    }

  @Test
  fun `chapter reentry reuses parsed spine item without rereading resources`() =
    runTest(testDispatcher) {
      val links = List(2) { mockk<Link>(relaxed = true) }
      val firstResource =
        mockk<Resource> {
          coEvery { read() } returns
            Try.success(
              "<html><body><p>${"First chapter ".repeat(6)}</p></body></html>".encodeToByteArray()
            )
        }
      val secondResource =
        mockk<Resource> {
          coEvery { read() } returns
            Try.success(
              "<html><body><p>${"Second chapter ".repeat(6)}</p></body></html>".encodeToByteArray()
            )
        }
      val publication =
        mockk<Publication>(relaxUnitFun = true) {
          every { readingOrder } returns links
          every { get(links[0]) } returns firstResource
          every { get(links[1]) } returns secondResource
        }
      val viewModel = viewModel(FakeReaderSource(1 to publication))

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(0, 1)
      viewModel.nextPage()
      advanceUntilIdle()
      viewModel.onPageCount(1, 1)
      viewModel.previousPage()
      advanceUntilIdle()
      viewModel.onPageCount(0, 1)
      viewModel.nextPage()
      advanceUntilIdle()

      assertEquals(1, viewModel.currentSpineIndex())
      coVerify(exactly = 1) { firstResource.read() }
      coVerify(exactly = 1) { secondResource.read() }
    }

  @Test
  fun `previousPage returns to visited chapter even when its resource became unreadable`() =
    runTest(testDispatcher) {
      val links = List(2) { mockk<Link>(relaxed = true) }
      val firstResource =
        mockk<Resource> {
          coEvery { read() } returns
            Try.success(
              "<html><body><p>${"First chapter ".repeat(6)}</p></body></html>".encodeToByteArray()
            )
        }
      val secondResource =
        mockk<Resource> {
          coEvery { read() } returns
            Try.success(
              "<html><body><p>${"Second chapter ".repeat(6)}</p></body></html>".encodeToByteArray()
            )
        }
      var firstResourceGets = 0
      val publication =
        mockk<Publication>(relaxUnitFun = true) {
          every { readingOrder } returns links
          every { get(links[0]) } answers
            {
              firstResourceGets += 1
              firstResource.takeIf { firstResourceGets == 1 }
            }
          every { get(links[1]) } returns secondResource
        }
      val viewModel = viewModel(FakeReaderSource(1 to publication))

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)
      viewModel.nextPage()
      advanceUntilIdle()
      assertEquals(1, viewModel.currentSpineIndex())
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)

      viewModel.previousPage()
      advanceUntilIdle()

      // Backward targets are always previously visited, so the spine item cache serves them
      // without touching the (now unreadable) resource.
      assertEquals(0, viewModel.currentSpineIndex())
      assertEquals(
        listOf("First chapter ".repeat(6).trim()),
        viewModel.currentDocument().paragraphText(),
      )
      assertEquals(0, viewModel.pageCount.value)
    }

  @Test
  fun `stale page count callback after chapter change is ignored`() =
    runTest(testDispatcher) {
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to
              testPublication(
                "<html><body><p>${"First chapter ".repeat(6)}</p></body></html>",
                "<html><body><p>${"Second chapter ".repeat(6)}</p></body></html>",
              )
          )
        )

      viewModel.open(1)
      advanceUntilIdle()
      val firstSpineIndex = viewModel.currentSpineIndex()
      viewModel.onPageCount(firstSpineIndex, 1)

      viewModel.nextPage()
      advanceUntilIdle()
      viewModel.onPageCount(firstSpineIndex, 99)

      assertEquals(0, viewModel.pageCount.value)
    }

  @Test
  fun `nextPage on last page ignores duplicate next spine open while loading`() =
    runTest(testDispatcher) {
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to
              testPublicationWithReadDelays(
                "<html><body><p>${"First chapter ".repeat(6)}</p></body></html>" to 0,
                "<html><body><p>${"Second chapter ".repeat(6)}</p></body></html>" to 1_000,
                "<html><body><p>${"Third chapter ".repeat(6)}</p></body></html>" to 0,
              )
          )
        )

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)

      viewModel.nextPage()
      viewModel.nextPage()
      advanceUntilIdle()

      val state = viewModel.uiState.value
      assertTrue(state is ReaderUiState.Reading)
      assertEquals(
        listOf("Second chapter ".repeat(6).trim()),
        (state as ReaderUiState.Reading).document.paragraphText(),
      )
    }

  @Test
  fun `opening another series cancels pending next spine open`() =
    runTest(testDispatcher) {
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to
              testPublicationWithReadDelays(
                "<html><body><p>${"First chapter ".repeat(6)}</p></body></html>" to 0,
                "<html><body><p>${"Stale chapter ".repeat(6)}</p></body></html>" to 1_000,
              ),
            2 to testPublication("<html><body><p>${"Fresh chapter ".repeat(6)}</p></body></html>"),
          )
        )

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)

      viewModel.nextPage()
      viewModel.open(2)
      advanceUntilIdle()

      val state = viewModel.uiState.value
      assertTrue(state is ReaderUiState.Reading)
      assertEquals(
        listOf("Fresh chapter ".repeat(6).trim()),
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

  private fun testPublication(vararg xhtml: String): Publication {
    val spine =
      xhtml.takeIf { items -> items.isNotEmpty() }
        ?: arrayOf("<html><body><p>${"Hello ".repeat(10)}</p></body></html>")
    val links = spine.indices.map { index -> mockk<Link>(relaxed = true) }
    val resources = spine.map { content ->
      mockk<Resource> { coEvery { read() } returns Try.success(content.encodeToByteArray()) }
    }
    return mockk(relaxUnitFun = true) {
      every { readingOrder } returns links
      links.forEachIndexed { index, link -> every { get(link) } returns resources[index] }
    }
  }

  private fun testPublicationWithReadDelays(vararg spine: Pair<String, Long>): Publication {
    val links = spine.indices.map { index -> mockk<Link>(relaxed = true) }
    val resources = spine.map { (content, readDelayMillis) ->
      mockk<Resource> {
        coEvery { read() } coAnswers
          {
            delay(readDelayMillis)
            Try.success(content.encodeToByteArray())
          }
      }
    }
    return mockk(relaxUnitFun = true) {
      every { readingOrder } returns links
      links.forEachIndexed { index, link -> every { get(link) } returns resources[index] }
    }
  }

  private fun testPublicationWithMissingResource(
    missingResourceIndex: Int,
    vararg xhtml: String,
  ): Publication {
    val links = xhtml.indices.map { mockk<Link>(relaxed = true) }
    val resources = xhtml.map { content ->
      mockk<Resource> { coEvery { read() } returns Try.success(content.encodeToByteArray()) }
    }
    return mockk(relaxUnitFun = true) {
      every { readingOrder } returns links
      links.forEachIndexed { index, link ->
        every { get(link) } returns resources[index].takeUnless { index == missingResourceIndex }
      }
    }
  }

  private fun ReaderViewModel.closeThroughStore() {
    ViewModelStore().apply {
      put("reader", this@closeThroughStore)
      clear()
    }
  }

  private fun ReaderViewModel.currentDocument(): ReaderDocument {
    val state = uiState.value
    assertTrue(state is ReaderUiState.Reading)
    return (state as ReaderUiState.Reading).document
  }

  private fun ReaderViewModel.currentSpineIndex(): Int {
    val state = uiState.value
    assertTrue(state is ReaderUiState.Reading)
    return (state as ReaderUiState.Reading).spineIndex
  }

  private fun ReaderViewModel.currentDiagnostics(): ParseDiagnostics {
    val state = uiState.value
    assertTrue(state is ReaderUiState.Reading)
    return (state as ReaderUiState.Reading).diagnostics
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
