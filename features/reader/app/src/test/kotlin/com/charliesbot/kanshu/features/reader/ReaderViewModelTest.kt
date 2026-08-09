package com.charliesbot.kanshu.features.reader

import android.graphics.RectF
import androidx.lifecycle.ViewModelStore
import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderHighlightColor
import com.charliesbot.kanshu.core.reader.ReaderMargins
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.reader.ReaderPreferencesRepository
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.ReaderSource
import com.charliesbot.kanshu.core.reader.annotation.AnnotationRepository
import com.charliesbot.kanshu.core.reader.annotation.ReaderAnnotation
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import com.charliesbot.kanshu.core.sync.InitialPosition
import com.charliesbot.kanshu.core.sync.RemoteProgress
import com.charliesbot.kanshu.core.sync.SyncRepository
import com.charliesbot.kanshu.navigator.ReaderHighlight
import com.charliesbot.kanshu.navigator.ReaderPagePositions
import com.charliesbot.kanshu.navigator.ReaderSelectionInfo
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
  fun `preferences reflect the repository not hardcoded defaults`() =
    runTest(testDispatcher) {
      val stored = ReaderPreferences(fontScale = 1.4f, alignment = ReaderAlignment.Left)
      val repository = FakeReaderPreferencesRepository(stored)
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()), repository)

      advanceUntilIdle()

      assertEquals(stored, viewModel.preferences.value)
    }

  @Test
  fun `preferences start from defaults before the repository emits`() =
    runTest(testDispatcher) {
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()))

      assertEquals(ReaderPreferences(), viewModel.preferences.value)
    }

  @Test
  fun `preference setters delegate to the repository`() =
    runTest(testDispatcher) {
      val repository = FakeReaderPreferencesRepository()
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()), repository)

      viewModel.setFontScale(1.2f)
      viewModel.setAlignment(ReaderAlignment.Left)
      viewModel.resetSpacing()
      advanceUntilIdle()

      assertEquals(1.2f, repository.current.fontScale)
      assertEquals(ReaderAlignment.Left, repository.current.alignment)
      assertTrue(repository.spacingReset)
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
  fun `failed open can retry the same series`() =
    runTest(testDispatcher) {
      val publication = testPublication()
      var attempts = 0
      val source =
        object : ReaderSource {
          override suspend fun openBook(seriesId: Int): ReaderResult {
            attempts++
            return if (attempts == 1) {
              ReaderResult.Error.NotFound
            } else {
              ReaderResult.Success(publication, File("test.epub"))
            }
          }
        }
      val viewModel = viewModel(source)

      viewModel.open(1)
      advanceUntilIdle()
      assertEquals(ReaderUiState.Error.NotFound, viewModel.uiState.value)

      viewModel.open(1)
      advanceUntilIdle()

      assertEquals(2, attempts)
      assertTrue(viewModel.uiState.value is ReaderUiState.Reading)
    }

  @Test
  fun `publication without a readable spine is closed and not exposed`() =
    runTest(testDispatcher) {
      val publication =
        mockk<Publication>(relaxUnitFun = true) { every { readingOrder } returns emptyList() }
      val viewModel = viewModel(FakeReaderSource(1 to publication))

      viewModel.open(1)
      advanceUntilIdle()

      assertEquals(ReaderUiState.Error.OpenFailed, viewModel.uiState.value)
      assertNull(viewModel.resourceLoader.value)
      verify(exactly = 1) { publication.close() }
    }

  @Test
  fun `late result from replaced open is ignored`() =
    runTest(testDispatcher) {
      val ioDispatcher = StandardTestDispatcher(testScheduler, name = "reader-io")
      val stalePublication = testPublication("<html><body><p>Stale</p></body></html>")
      val freshPublication = testPublication("<html><body><p>Fresh</p></body></html>")
      val source =
        object : ReaderSource {
          override suspend fun openBook(seriesId: Int): ReaderResult {
            if (seriesId == 1) {
              try {
                delay(1_000)
              } catch (_: CancellationException) {
                // Simulates a source that completes despite cancellation.
              }
              return ReaderResult.Success(stalePublication, File("stale.epub"))
            }
            return ReaderResult.Success(freshPublication, File("fresh.epub"))
          }
        }
      val viewModel = viewModel(source, ioDispatcher = ioDispatcher)

      viewModel.open(1)
      runCurrent()
      viewModel.open(2)
      advanceUntilIdle()

      assertEquals(listOf("Fresh"), viewModel.currentDocument().paragraphText())
      verify(exactly = 1) { stalePublication.close() }
      verify(exactly = 0) { freshPublication.close() }
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
      assertEquals(0, viewModel.pagination.value.currentPage)
      assertEquals(0, viewModel.pagination.value.pageCount)
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
      assertEquals(0, viewModel.pagination.value.currentPage)
      assertEquals(0, viewModel.pagination.value.pageCount)
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
      assertEquals(0, viewModel.pagination.value.currentPage)
      assertEquals(0, viewModel.pagination.value.pageCount)
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
      assertEquals(0, viewModel.pagination.value.currentPage)
      assertEquals(1, viewModel.pagination.value.pageCount)
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
      assertEquals(0, viewModel.pagination.value.currentPage)
      assertEquals(1, viewModel.pagination.value.pageCount)
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

      assertEquals(0, viewModel.pagination.value.currentPage)
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
      assertEquals(0, viewModel.pagination.value.pageCount)
      assertEquals(0, viewModel.pagination.value.currentPage)

      viewModel.onPageCount(viewModel.currentSpineIndex(), 4)

      assertEquals(3, viewModel.pagination.value.currentPage)
      assertEquals(4, viewModel.pagination.value.pageCount)
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
      assertEquals(0, viewModel.pagination.value.currentPage)
      assertEquals(1, viewModel.pagination.value.pageCount)
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
      assertEquals(0, viewModel.pagination.value.currentPage)
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
  fun `openLink navigates to the spine item matching the href path`() =
    runTest(testDispatcher) {
      val links =
        listOf("OEBPS/ch01.xhtml", "OEBPS/ch02.xhtml").map { path ->
          mockk<Link>(relaxed = true) {
            every { url(any(), any()) } returns checkNotNull(Url(path))
          }
        }
      val resources =
        listOf("First chapter ", "Second chapter ").map { text ->
          mockk<Resource> {
            coEvery { read() } returns
              Try.success("<html><body><p>${text.repeat(6)}</p></body></html>".encodeToByteArray())
          }
        }
      val publication =
        mockk<Publication>(relaxUnitFun = true) {
          every { readingOrder } returns links
          links.forEachIndexed { index, link -> every { get(link) } returns resources[index] }
          every { get(any<Url>()) } returns null
        }
      val viewModel = viewModel(FakeReaderSource(1 to publication))

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)

      viewModel.openLink("OEBPS/ch02.xhtml#section3")
      advanceUntilIdle()

      assertEquals(1, viewModel.currentSpineIndex())
    }

  @Test
  fun `openLink with unresolvable href stays put`() =
    runTest(testDispatcher) {
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()))

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)

      viewModel.openLink("OEBPS/missing.xhtml")
      advanceUntilIdle()

      assertEquals(0, viewModel.currentSpineIndex())
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
      viewModel.onPageCount(viewModel.currentChapterToken(), 1)
      viewModel.nextPage()
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentChapterToken(), 1)
      viewModel.previousPage()
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentChapterToken(), 1)
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
      assertEquals(0, viewModel.pagination.value.pageCount)
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

      assertEquals(0, viewModel.pagination.value.pageCount)
    }

  @Test
  fun `stale page count callback after returning to the same spine is ignored`() =
    runTest(testDispatcher) {
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to
              testPublication(
                "<html><body><p>First</p></body></html>",
                "<html><body><p>Second</p></body></html>",
              )
          )
        )

      viewModel.open(1)
      advanceUntilIdle()
      val firstVisitToken = viewModel.currentChapterToken()
      viewModel.onPageCount(firstVisitToken, 1)
      viewModel.nextPage()
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentSpineIndex(), 1)
      viewModel.previousPage()
      advanceUntilIdle()

      viewModel.onPageCount(firstVisitToken, 99)

      assertEquals(0, viewModel.pagination.value.pageCount)
    }

  @Test
  fun `stale page positions from another book with the same spine are ignored`() =
    runTest(testDispatcher) {
      val sync = FakeSyncRepository()
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to testPublication("<html><body><p>Old book</p></body></html>"),
            2 to testPublication("<html><body><p>New book</p></body></html>"),
          ),
          syncRepository = sync,
        )

      viewModel.open(1)
      advanceUntilIdle()
      val oldChapterToken = viewModel.currentChapterToken()
      viewModel.open(2)
      advanceUntilIdle()

      viewModel.onPagePositions(
        oldChapterToken,
        ReaderPagePositions(pageStartCharOffsets = listOf(0), textStreamLength = 100),
      )

      assertTrue(sync.saved.isEmpty())
    }

  @Test
  fun `stale layout failure does not replace the current book`() =
    runTest(testDispatcher) {
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to testPublication("<html><body><p>Old book</p></body></html>"),
            2 to testPublication("<html><body><p>New book</p></body></html>"),
          )
        )

      viewModel.open(1)
      advanceUntilIdle()
      val oldChapterToken = viewModel.currentChapterToken()
      viewModel.open(2)
      advanceUntilIdle()

      viewModel.onLayoutFailed(oldChapterToken)

      assertTrue(viewModel.uiState.value is ReaderUiState.Reading)
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
  fun `stored position reopens its chapter at the page containing its char offset`() =
    runTest(testDispatcher) {
      val sync =
        FakeSyncRepository(
          stored = ReaderPosition(spineIndex = 1, charOffset = 150, progressInSpine = 0.5f)
        )
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to
              testPublication(
                "<html><body><p>${"First chapter ".repeat(6)}</p></body></html>",
                "<html><body><p>${"Second chapter ".repeat(6)}</p></body></html>",
              )
          ),
          syncRepository = sync,
        )

      viewModel.open(1)
      advanceUntilIdle()
      assertEquals(1, viewModel.currentSpineIndex())

      viewModel.onPagePositions(
        viewModel.currentChapterToken(),
        ReaderPagePositions(pageStartCharOffsets = listOf(0, 100, 200), textStreamLength = 300),
      )

      assertEquals(1, viewModel.pagination.value.currentPage)
    }

  @Test
  fun `repagination keeps the reader on the same text, not the same page index`() =
    runTest(testDispatcher) {
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()))

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentChapterToken(), 3)
      viewModel.onPagePositions(
        viewModel.currentChapterToken(),
        ReaderPagePositions(pageStartCharOffsets = listOf(0, 100, 200), textStreamLength = 300),
      )
      viewModel.nextPage()
      assertEquals(1, viewModel.pagination.value.currentPage)

      // Shrinking the font repaginates the chapter into more, smaller pages. Character offset
      // 100 now lives on page 2 — holding the old index would show different text.
      viewModel.onPageCount(viewModel.currentChapterToken(), 6)
      viewModel.onPagePositions(
        viewModel.currentChapterToken(),
        ReaderPagePositions(
          pageStartCharOffsets = listOf(0, 50, 100, 150, 200, 250),
          textStreamLength = 300,
        ),
      )

      assertEquals(2, viewModel.pagination.value.currentPage)
    }

  @Test
  fun `repagination at an unchanged position keeps the same page`() =
    runTest(testDispatcher) {
      val sync =
        FakeSyncRepository(
          stored = ReaderPosition(spineIndex = 0, charOffset = 200, progressInSpine = 0.6f)
        )
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()), syncRepository = sync)
      val positions =
        ReaderPagePositions(pageStartCharOffsets = listOf(0, 100, 200), textStreamLength = 300)

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPagePositions(viewModel.currentChapterToken(), positions)
      assertEquals(2, viewModel.pagination.value.currentPage)

      viewModel.onPageCount(viewModel.currentChapterToken(), 3)
      viewModel.previousPage()
      assertEquals(1, viewModel.pagination.value.currentPage)
      viewModel.onPagePositions(viewModel.currentChapterToken(), positions)

      assertEquals(1, viewModel.pagination.value.currentPage)
    }

  @Test
  fun `stored position for an unreadable chapter falls back to the first spine item`() =
    runTest(testDispatcher) {
      val sync =
        FakeSyncRepository(
          stored = ReaderPosition(spineIndex = 1, charOffset = 150, progressInSpine = 0.5f)
        )
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to
              testPublicationWithMissingResource(
                missingResourceIndex = 1,
                "<html><body><p>${"First chapter ".repeat(6)}</p></body></html>",
                "<html><body><p>${"Second chapter ".repeat(6)}</p></body></html>",
              )
          ),
          syncRepository = sync,
        )

      viewModel.open(1)
      advanceUntilIdle()

      assertEquals(0, viewModel.currentSpineIndex())
      // The offset belonged to the chapter we couldn't open; it must not seek within this one.
      viewModel.onPagePositions(
        viewModel.currentChapterToken(),
        ReaderPagePositions(pageStartCharOffsets = listOf(0, 100, 200), textStreamLength = 300),
      )
      assertEquals(0, viewModel.pagination.value.currentPage)
    }

  @Test
  fun `a resume that lands mid-page reports the page start, not the stored offset`() =
    runTest(testDispatcher) {
      // Typography changed between sessions, so the stored offset no longer starts a page. The
      // reader lands on the page containing it, which is behind the stored offset — the sync
      // layer's pre-push check is what stops that going to the server, so the reader must report
      // it honestly rather than echoing the stored value back.
      val sync =
        FakeSyncRepository(
          stored = ReaderPosition(spineIndex = 0, charOffset = 150, progressInSpine = 0.375f)
        )
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()), syncRepository = sync)

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentChapterToken(), 3)
      viewModel.onPagePositions(
        viewModel.currentChapterToken(),
        ReaderPagePositions(pageStartCharOffsets = listOf(0, 100, 200), textStreamLength = 400),
      )

      assertEquals(
        listOf(ReaderPosition(spineIndex = 0, charOffset = 100, progressInSpine = 0.25f)),
        sync.saved,
      )
    }

  @Test
  fun `opening a book reports the position it resumed from and nothing else`() =
    runTest(testDispatcher) {
      // The sync layer drops a position matching the one it handed out, which is what stops a
      // stale local row from clobbering a further-ahead server. That only holds if the reader
      // reports the resumed position exactly rather than something near it.
      val resumed = ReaderPosition(spineIndex = 0, charOffset = 100, progressInSpine = 0.25f)
      val sync = FakeSyncRepository(stored = resumed)
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()), syncRepository = sync)

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentChapterToken(), 3)
      viewModel.onPagePositions(
        viewModel.currentChapterToken(),
        ReaderPagePositions(pageStartCharOffsets = listOf(0, 100, 200), textStreamLength = 400),
      )

      assertEquals(listOf(resumed), sync.saved)
    }

  @Test
  fun `opening a book does not consult the remote`() =
    runTest(testDispatcher) {
      // The open path must stay local-only: a slow or unreachable host would otherwise sit in
      // front of the first page.
      val sync = FakeSyncRepository()
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()), syncRepository = sync)

      viewModel.open(1)
      advanceUntilIdle()

      assertEquals(false, sync.remoteConsulted)
    }

  @Test
  fun `repagination re-reports the current position, not a shifted one`() =
    runTest(testDispatcher) {
      val sync = FakeSyncRepository()
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()), syncRepository = sync)
      val positions =
        ReaderPagePositions(pageStartCharOffsets = listOf(0, 100, 200), textStreamLength = 400)

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentChapterToken(), 3)
      viewModel.onPagePositions(viewModel.currentChapterToken(), positions)
      viewModel.nextPage()

      // A preference change repaginates the same chapter at the same position.
      viewModel.onPagePositions(viewModel.currentChapterToken(), positions)

      // Two distinct positions total — the landing page and the page turned to. The repagination
      // re-reports the second rather than introducing a third, which is what lets the sync layer
      // drop it as redundant.
      assertEquals(2, sync.saved.distinct().size)
      // Specifically the page turned to, re-reported. A repagination that reported the landing
      // page again would also leave two distinct values but would send the reader backwards.
      assertEquals(sync.saved[sync.saved.size - 2], sync.saved.last())
    }

  @Test
  fun `page turns persist the char offset of the page landed on`() =
    runTest(testDispatcher) {
      val sync = FakeSyncRepository()
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()), syncRepository = sync)

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentChapterToken(), 3)
      viewModel.onPagePositions(
        viewModel.currentChapterToken(),
        ReaderPagePositions(pageStartCharOffsets = listOf(0, 100, 200), textStreamLength = 400),
      )
      viewModel.nextPage()

      val last = sync.saved.last()
      assertEquals(0, last.spineIndex)
      assertEquals(100, last.charOffset)
      assertEquals(0.25f, last.progressInSpine)
    }

  @Test
  fun `a tap that cannot move reports no new position`() =
    runTest(testDispatcher) {
      // Last page of the last chapter: nextPage has nowhere to go, so every report is the position
      // the reader was already at and the sync layer drops all but the first.
      val sync = FakeSyncRepository()
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()), syncRepository = sync)
      val positions = ReaderPagePositions(pageStartCharOffsets = listOf(0), textStreamLength = 100)

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentChapterToken(), 1)
      viewModel.onPagePositions(viewModel.currentChapterToken(), positions)
      viewModel.nextPage()
      advanceUntilIdle()
      viewModel.onPagePositions(viewModel.currentChapterToken(), positions)

      assertEquals(1, sync.saved.distinct().size)
    }

  @Test
  fun `progress is not persisted before the offsets table arrives`() =
    runTest(testDispatcher) {
      val sync = FakeSyncRepository()
      val viewModel = viewModel(FakeReaderSource(1 to testPublication()), syncRepository = sync)

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.onPageCount(viewModel.currentChapterToken(), 3)
      viewModel.nextPage()

      // Without offsets there is no stable position to write — a page index alone is the very
      // thing character offsets exist to stop persisting.
      assertTrue(sync.saved.isEmpty())
    }

  @Test
  fun `addHighlight stores the selection range for the current chapter`() =
    runTest(testDispatcher) {
      val annotations = FakeAnnotationRepository()
      val viewModel =
        viewModel(FakeReaderSource(1 to testPublication()), annotationRepository = annotations)

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.addHighlight(
        ReaderSelectionInfo(
          text = "highlighted words",
          anchor = RectF(),
          startCharOffset = 40,
          endCharOffset = 57,
        )
      )
      advanceUntilIdle()

      val stored = annotations.saved.single()
      assertEquals(0, stored.spineIndex)
      assertEquals(40, stored.startCharOffset)
      assertEquals(57, stored.endCharOffset)
      assertEquals("highlighted words", stored.selectedText)
      assertEquals(
        listOf(ReaderHighlight(40, 57, id = "annotation-0")),
        viewModel.highlights.value,
      )
    }

  @Test
  fun `removeHighlight deletes the tapped annotation`() =
    runTest(testDispatcher) {
      val annotations = FakeAnnotationRepository()
      val viewModel =
        viewModel(FakeReaderSource(1 to testPublication()), annotationRepository = annotations)
      viewModel.open(1)
      advanceUntilIdle()
      viewModel.addHighlight(
        ReaderSelectionInfo("words", RectF(), startCharOffset = 4, endCharOffset = 9),
        ReaderHighlightColor.Pink,
      )
      advanceUntilIdle()

      viewModel.removeHighlight(annotations.saved.single().id)
      advanceUntilIdle()

      assertTrue(annotations.saved.isEmpty())
      assertTrue(viewModel.highlights.value.isEmpty())
    }

  @Test
  fun `setHighlightColor recolors the tapped annotation`() =
    runTest(testDispatcher) {
      val annotations = FakeAnnotationRepository()
      val viewModel =
        viewModel(FakeReaderSource(1 to testPublication()), annotationRepository = annotations)
      viewModel.open(1)
      advanceUntilIdle()
      viewModel.addHighlight(
        ReaderSelectionInfo("words", RectF(), startCharOffset = 4, endCharOffset = 9),
        ReaderHighlightColor.Pink,
      )
      advanceUntilIdle()

      viewModel.setHighlightColor(annotations.saved.single().id, ReaderHighlightColor.Aqua)
      advanceUntilIdle()

      assertEquals(ReaderHighlightColor.Aqua, annotations.saved.single().color)
      assertEquals(ReaderHighlightColor.Aqua, viewModel.highlights.value.single().color)
    }

  @Test
  fun `an empty selection range is not stored`() =
    runTest(testDispatcher) {
      val annotations = FakeAnnotationRepository()
      val viewModel =
        viewModel(FakeReaderSource(1 to testPublication()), annotationRepository = annotations)

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.addHighlight(
        ReaderSelectionInfo(text = "", anchor = RectF(), startCharOffset = 5, endCharOffset = 5)
      )
      advanceUntilIdle()

      assertTrue(annotations.saved.isEmpty())
    }

  @Test
  fun `highlights are scoped to the chapter on screen`() =
    runTest(testDispatcher) {
      val annotations = FakeAnnotationRepository()
      val viewModel =
        viewModel(
          FakeReaderSource(
            1 to
              testPublication(
                "<html><body><p>${"First chapter ".repeat(6)}</p></body></html>",
                "<html><body><p>${"Second chapter ".repeat(6)}</p></body></html>",
              )
          ),
          annotationRepository = annotations,
        )

      viewModel.open(1)
      advanceUntilIdle()
      viewModel.addHighlight(
        ReaderSelectionInfo(text = "one", anchor = RectF(), startCharOffset = 1, endCharOffset = 4)
      )
      advanceUntilIdle()
      assertEquals(
        listOf(ReaderHighlight(1, 4, id = "annotation-0")),
        viewModel.highlights.value,
      )

      viewModel.onPageCount(viewModel.currentChapterToken(), 1)
      viewModel.nextPage()
      advanceUntilIdle()

      // Chapter 2 has none of its own, and must not inherit chapter 1's offsets.
      assertEquals(1, viewModel.currentSpineIndex())
      assertEquals(emptyList<ReaderHighlight>(), viewModel.highlights.value)
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

  private fun viewModel(
    source: ReaderSource,
    preferencesRepository: ReaderPreferencesRepository = FakeReaderPreferencesRepository(),
    syncRepository: SyncRepository = FakeSyncRepository(),
    annotationRepository: AnnotationRepository = FakeAnnotationRepository(),
    ioDispatcher: CoroutineDispatcher = testDispatcher,
  ): ReaderViewModel =
    ReaderViewModel(
      OpenBookUseCase(source),
      preferencesRepository,
      syncRepository,
      annotationRepository,
      ioDispatcher = ioDispatcher,
    )

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

  private fun ReaderViewModel.currentChapterToken(): Long {
    val state = uiState.value
    assertTrue(state is ReaderUiState.Reading)
    return (state as ReaderUiState.Reading).chapterToken
  }

  private fun ReaderViewModel.onPageCount(spineIndex: Int, count: Int) {
    val reading = currentReading()
    onPageCount(
      chapterToken =
        reading.chapterToken.takeIf { reading.spineIndex == spineIndex } ?: Long.MIN_VALUE,
      count = count,
    )
  }

  private fun ReaderViewModel.onPagePositions(
    spineIndex: Int,
    positions: ReaderPagePositions,
  ) {
    val reading = currentReading()
    onPagePositions(
      chapterToken =
        reading.chapterToken.takeIf { reading.spineIndex == spineIndex } ?: Long.MIN_VALUE,
      positions = positions,
    )
  }

  private fun ReaderViewModel.currentReading(): ReaderUiState.Reading {
    val state = uiState.value
    assertTrue(state is ReaderUiState.Reading)
    return state as ReaderUiState.Reading
  }

  private fun ReaderViewModel.currentDiagnostics(): ParseDiagnostics {
    val state = uiState.value
    assertTrue(state is ReaderUiState.Reading)
    return (state as ReaderUiState.Reading).diagnostics
  }
}

private class FakeReaderPreferencesRepository(initial: ReaderPreferences = ReaderPreferences()) :
  ReaderPreferencesRepository {
  private val state = MutableStateFlow(initial)
  val current: ReaderPreferences
    get() = state.value

  var spacingReset = false
    private set

  override val preferences: Flow<ReaderPreferences> = state

  override suspend fun setFont(font: ReaderFont) = state.update { it.copy(font = font) }

  override suspend fun setFontScale(scale: Float) = state.update { it.copy(fontScale = scale) }

  override suspend fun setBoldness(value: Float) = state.update { it.copy(boldness = value) }

  override suspend fun setMargins(margins: ReaderMargins) = state.update {
    it.copy(margins = margins)
  }

  override suspend fun setAlignment(alignment: ReaderAlignment) = state.update {
    it.copy(alignment = alignment)
  }

  override suspend fun setLineSpacing(value: Float) = state.update { it.copy(lineSpacing = value) }

  override suspend fun setParagraphSpacing(value: Float) = state.update {
    it.copy(paragraphSpacing = value)
  }

  override suspend fun setWordSpacing(value: Float) = state.update { it.copy(wordSpacing = value) }

  override suspend fun setLetterSpacing(value: Float) = state.update {
    it.copy(letterSpacing = value)
  }

  override suspend fun resetSpacing() {
    spacingReset = true
  }
}

private class FakeAnnotationRepository : AnnotationRepository {
  private val stored = MutableStateFlow<List<ReaderAnnotation>>(emptyList())
  val saved: List<ReaderAnnotation>
    get() = stored.value

  override fun observeForSpine(bookId: String, spineIndex: Int): Flow<List<ReaderAnnotation>> =
    stored.map { all ->
      all.filter { it.spineIndex == spineIndex }
    }

  override suspend fun addHighlight(
    bookId: String,
    spineIndex: Int,
    startCharOffset: Int,
    endCharOffset: Int,
    selectedText: String,
    color: ReaderHighlightColor,
  ): ReaderAnnotation? {
    if (endCharOffset <= startCharOffset) return null
    val annotation =
      ReaderAnnotation(
        id = "annotation-${stored.value.size}",
        spineIndex = spineIndex,
        startCharOffset = startCharOffset,
        endCharOffset = endCharOffset,
        selectedText = selectedText,
        color = color,
      )
    stored.update { it + annotation }
    return annotation
  }

  override suspend fun updateHighlightColor(id: String, color: ReaderHighlightColor) =
    stored.update { all ->
      all.map { if (it.id == id) it.copy(color = color) else it }
    }

  override suspend fun delete(id: String) = stored.update { all -> all.filterNot { it.id == id } }
}

private class FakeSyncRepository(private val stored: ReaderPosition? = null) : SyncRepository {
  val saved = mutableListOf<ReaderPosition>()
  var remoteConsulted = false
    private set

  override suspend fun localPosition(bookId: String): ReaderPosition? = stored

  override suspend fun resolveInitialPosition(
    bookId: String,
    file: File,
    publication: Publication,
  ): InitialPosition {
    remoteConsulted = true
    return InitialPosition.UseLocal(stored)
  }

  override fun setProgress(
    bookId: String,
    file: File,
    position: ReaderPosition,
    publication: Publication,
  ) {
    saved += position
  }

  override suspend fun flushProgress(
    bookId: String,
    file: File,
    position: ReaderPosition,
    publication: Publication,
  ) = Unit

  override suspend fun pullFurthestPosition(
    bookId: String,
    file: File,
    publication: Publication,
  ): RemoteProgress? = null
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
