package com.charliesbot.kanshu.features.reader

import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderMargins
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.reader.ReaderPreferencesRepository
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import com.charliesbot.kanshu.core.sync.InitialPosition
import com.charliesbot.kanshu.core.sync.SyncRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val openBook: OpenBookUseCase = mockk()
  private val sync: SyncRepository =
    mockk(relaxed = true) {
      coEvery { resolveInitialPosition(any(), any(), any()) } returns InitialPosition.UseLocal(null)
    }
  private val fakeFile = File("/dev/null/fake.epub")
  private lateinit var preferences: FakeReaderPreferencesRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    preferences = FakeReaderPreferencesRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun fakePublication(title: String? = "A Book"): Publication =
    mockk<Publication>(relaxed = true).also { pub ->
      val metadata = mockk<org.readium.r2.shared.publication.Metadata>(relaxed = true)
      every { metadata.title } returns title
      every { pub.metadata } returns metadata

      val link =
        mockk<org.readium.r2.shared.publication.Link>(relaxed = true) {
          every { href.toString() } returns "OEBPS/chapter1.xhtml"
          every { mediaType } returns
            org.readium.r2.shared.util.mediatype.MediaType("application/xhtml+xml")!!
        }
      every { pub.readingOrder } returns listOf(link)

      val resource = mockk<org.readium.r2.shared.util.resource.Resource>(relaxed = true)
      coEvery { resource.read() } returns
        org.readium.r2.shared.util.Try.success("<html><body>Chapter 1</body></html>".toByteArray())
      every { resource.close() } returns Unit
      every { pub.get(any<org.readium.r2.shared.publication.Link>()) } returns resource
    }

  private fun newViewModel(): ReaderViewModel =
    ReaderViewModel(
      seriesId = 1,
      openBook = openBook,
      sync = sync,
      preferences = preferences,
      ioDispatcher = testDispatcher,
    )

  @Test
  fun `success result becomes Ready with title and publication`() = runTest {
    val publication = fakePublication(title = "A Book")
    coEvery { openBook(any()) } returns ReaderResult.Success(publication, fakeFile)

    val viewModel = newViewModel()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state is ReaderUiState.Ready)
    state as ReaderUiState.Ready
    assertEquals("A Book", state.title)
    assertEquals(publication, state.publication)
  }

  @Test
  fun `parse failed result becomes ParseFailed state`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Error.ParseFailed
    val viewModel = newViewModel()
    advanceUntilIdle()
    assertEquals(ReaderUiState.Error.ParseFailed, viewModel.uiState.value)
  }

  @Test
  fun `read failed result becomes ReadFailed state`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Error.ReadFailed
    val viewModel = newViewModel()
    advanceUntilIdle()
    assertEquals(ReaderUiState.Error.ReadFailed, viewModel.uiState.value)
  }

  @Test
  fun `not found result becomes NotFound state`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Error.NotFound
    val viewModel = newViewModel()
    advanceUntilIdle()
    assertEquals(ReaderUiState.Error.NotFound, viewModel.uiState.value)
  }

  @Test
  fun `persisted preferences seed Ready_initialPreferences`() = runTest {
    preferences.seed(ReaderPreferences(font = ReaderFont.OpenDyslexic, fontScale = 1.4f))
    coEvery { openBook(any()) } returns ReaderResult.Success(fakePublication(), fakeFile)

    val viewModel = newViewModel()
    advanceUntilIdle()

    val state = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(ReaderFont.OpenDyslexic, state.initialPreferences.font)
    assertEquals(1.4f, state.initialPreferences.fontScale, 0.0001f)
  }

  @Test
  fun `setFont persists the chosen family`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Success(fakePublication(), fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    viewModel.setFont(ReaderFont.OpenDyslexic)
    advanceUntilIdle()

    assertEquals(ReaderFont.OpenDyslexic, viewModel.readerPreferences.value.font)
  }

  @Test
  fun `setFontScale clamps below the min`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Success(fakePublication(), fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    viewModel.setFontScale(0.1f)
    advanceUntilIdle()

    assertEquals(ReaderPreferences.SCALE_MIN, viewModel.readerPreferences.value.fontScale, 0.0001f)
  }

  @Test
  fun `setFontScale clamps above the max`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Success(fakePublication(), fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    viewModel.setFontScale(5.0f)
    advanceUntilIdle()

    assertEquals(ReaderPreferences.SCALE_MAX, viewModel.readerPreferences.value.fontScale, 0.0001f)
  }

  @Test
  fun `setMargins persists the chosen margins`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Success(fakePublication(), fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    viewModel.setMargins(ReaderMargins.Compact)
    advanceUntilIdle()

    assertEquals(ReaderMargins.Compact, viewModel.readerPreferences.value.margins)
  }

  @Test
  fun `setAlignment persists the chosen alignment`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Success(fakePublication(), fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    viewModel.setAlignment(ReaderAlignment.Left)
    advanceUntilIdle()

    assertEquals(ReaderAlignment.Left, viewModel.readerPreferences.value.alignment)
  }

  @Test
  fun `setLineSpacing persists the chosen line spacing`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Success(fakePublication(), fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    viewModel.setLineSpacing(1.6f)
    advanceUntilIdle()

    assertEquals(1.6f, viewModel.readerPreferences.value.lineSpacing, 0.0001f)
  }

  @Test
  fun `setParagraphSpacing persists the chosen paragraph spacing`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Success(fakePublication(), fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    viewModel.setParagraphSpacing(1.0f)
    advanceUntilIdle()

    assertEquals(1.0f, viewModel.readerPreferences.value.paragraphSpacing, 0.0001f)
  }

  @Test
  fun `setWordSpacing persists the chosen word spacing`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Success(fakePublication(), fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    viewModel.setWordSpacing(0.3f)
    advanceUntilIdle()

    assertEquals(0.3f, viewModel.readerPreferences.value.wordSpacing, 0.0001f)
  }

  @Test
  fun `setLetterSpacing persists the chosen letter spacing`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Success(fakePublication(), fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    viewModel.setLetterSpacing(0.15f)
    advanceUntilIdle()

    assertEquals(0.15f, viewModel.readerPreferences.value.letterSpacing, 0.0001f)
  }

  @Test
  fun `resetSpacing resets all spacing parameters`() = runTest {
    coEvery { openBook(any()) } returns ReaderResult.Success(fakePublication(), fakeFile)
    preferences.seed(
      ReaderPreferences(
        lineSpacing = 1.8f,
        paragraphSpacing = 1.5f,
        wordSpacing = 0.4f,
        letterSpacing = 0.2f,
      )
    )
    val viewModel = newViewModel()
    advanceUntilIdle()

    viewModel.resetSpacing()
    advanceUntilIdle()

    val currentPrefs = viewModel.readerPreferences.value
    assertEquals(ReaderPreferences.LINE_SPACING_DEFAULT, currentPrefs.lineSpacing, 0.0001f)
    assertEquals(
      ReaderPreferences.PARAGRAPH_SPACING_DEFAULT,
      currentPrefs.paragraphSpacing,
      0.0001f,
    )
    assertEquals(ReaderPreferences.WORD_SPACING_DEFAULT, currentPrefs.wordSpacing, 0.0001f)
    assertEquals(ReaderPreferences.LETTER_SPACING_DEFAULT, currentPrefs.letterSpacing, 0.0001f)
  }

  private fun fakeMultiChapterPublication(
    title: String = "Multi Book",
    chapters: List<String> = listOf("OEBPS/chapter1.xhtml", "OEBPS/chapter2.xhtml"),
  ): Publication =
    mockk<Publication>(relaxed = true).also { pub ->
      val metadata = mockk<org.readium.r2.shared.publication.Metadata>(relaxed = true)
      every { metadata.title } returns title
      every { pub.metadata } returns metadata

      val links =
        chapters.mapIndexed { index, path ->
          mockk<org.readium.r2.shared.publication.Link>(relaxed = true) {
            every { href.toString() } returns path
            every { mediaType } returns
              org.readium.r2.shared.util.mediatype.MediaType("application/xhtml+xml")!!
          }
        }
      every { pub.readingOrder } returns links

      every { pub.get(any<org.readium.r2.shared.publication.Link>()) } answers
        {
          val requestedLink = firstArg<org.readium.r2.shared.publication.Link>()
          val index =
            links
              .indexOfFirst { it.href.toString() == requestedLink.href.toString() }
              .coerceAtLeast(0)
          val resource = mockk<org.readium.r2.shared.util.resource.Resource>(relaxed = true)
          coEvery { resource.read() } returns
            org.readium.r2.shared.util.Try.success(
              "<html><body>Chapter ${index + 1}</body></html>".toByteArray()
            )
          every { resource.close() } returns Unit
          resource
        }
    }

  @Test
  fun `goForward within chapter emits evaluateJs scrollToPage`() = runTest {
    val publication = fakePublication()
    coEvery { openBook(any()) } returns ReaderResult.Success(publication, fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    val readyState = viewModel.uiState.value as ReaderUiState.Ready
    val loadId = readyState.currentChapter.loadId
    viewModel.handleBridgeEvent(
      BridgeEvent.Repaginated(
        chapterLoadId = loadId,
        settingsRevision = 1,
        pageCount = 3,
        restoredPageIndex = 0,
        stalled = false,
      )
    )
    advanceUntilIdle()

    assertEquals(0, viewModel.pageIndex.value)
    assertEquals(3, viewModel.pageCount.value)

    val deferred = async { viewModel.evaluateJs.first() }
    runCurrent()
    viewModel.goForward()
    advanceUntilIdle()

    assertEquals("kanshu.scrollToPage(1)", deferred.await())
  }

  @Test
  fun `goBackward within chapter emits evaluateJs scrollToPage`() = runTest {
    val publication = fakePublication()
    coEvery { openBook(any()) } returns ReaderResult.Success(publication, fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    val readyState = viewModel.uiState.value as ReaderUiState.Ready
    val loadId = readyState.currentChapter.loadId
    viewModel.handleBridgeEvent(
      BridgeEvent.Repaginated(
        chapterLoadId = loadId,
        settingsRevision = 1,
        pageCount = 3,
        restoredPageIndex = 2,
        stalled = false,
      )
    )
    advanceUntilIdle()

    assertEquals(2, viewModel.pageIndex.value)

    val deferred = async { viewModel.evaluateJs.first() }
    runCurrent()
    viewModel.goBackward()
    advanceUntilIdle()

    assertEquals("kanshu.scrollToPage(1)", deferred.await())
  }

  @Test
  fun `goForward at last page of chapter loads next chapter`() = runTest {
    val publication = fakeMultiChapterPublication()
    coEvery { openBook(any()) } returns ReaderResult.Success(publication, fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    val readyState = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(0, readyState.currentChapter.spineIndex)

    val loadId = readyState.currentChapter.loadId
    viewModel.handleBridgeEvent(
      BridgeEvent.Repaginated(
        chapterLoadId = loadId,
        settingsRevision = 1,
        pageCount = 3,
        restoredPageIndex = 2,
        stalled = false,
      )
    )
    advanceUntilIdle()

    viewModel.goForward()
    advanceUntilIdle()

    val nextState = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(1, nextState.currentChapter.spineIndex)
    assertEquals(0, viewModel.pageIndex.value)
  }

  @Test
  fun `goBackward at first page of chapter loads previous chapter`() = runTest {
    val publication = fakeMultiChapterPublication()
    coEvery { openBook(any()) } returns ReaderResult.Success(publication, fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    viewModel.loadSpineChapter(1, targetPageIndex = 0)
    advanceUntilIdle()

    val readyState = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(1, readyState.currentChapter.spineIndex)
    assertEquals(0, viewModel.pageIndex.value)

    viewModel.goBackward()
    advanceUntilIdle()

    val prevState = viewModel.uiState.value as ReaderUiState.Ready
    assertEquals(0, prevState.currentChapter.spineIndex)
    assertEquals(9999, viewModel.pageIndex.value)
  }

  @Test
  fun `handleBridgeEvent PageSettled updates progress and index`() = runTest {
    val publication = fakePublication()
    coEvery { openBook(any()) } returns ReaderResult.Success(publication, fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    val readyState = viewModel.uiState.value as ReaderUiState.Ready
    val loadId = readyState.currentChapter.loadId

    viewModel.handleBridgeEvent(
      BridgeEvent.PageSettled(chapterLoadId = loadId, pageIndex = 1, progressInSpine = 0.5f)
    )
    advanceUntilIdle()

    assertEquals(1, viewModel.pageIndex.value)
  }

  @Test
  fun `handleBridgeEvent rejects stale loadId`() = runTest {
    val publication = fakePublication()
    coEvery { openBook(any()) } returns ReaderResult.Success(publication, fakeFile)
    val viewModel = newViewModel()
    advanceUntilIdle()

    val readyState = viewModel.uiState.value as ReaderUiState.Ready
    val currentLoadId = readyState.currentChapter.loadId

    val staleLoadId = currentLoadId - 1
    viewModel.handleBridgeEvent(
      BridgeEvent.PageSettled(chapterLoadId = staleLoadId, pageIndex = 5, progressInSpine = 0.5f)
    )
    advanceUntilIdle()

    assertEquals(0, viewModel.pageIndex.value)
  }

  private class FakeReaderPreferencesRepository : ReaderPreferencesRepository {
    private val state = MutableStateFlow(ReaderPreferences())
    override val preferences: Flow<ReaderPreferences> = state.asStateFlow()

    fun seed(value: ReaderPreferences) {
      state.value = value
    }

    override suspend fun setFont(font: ReaderFont) {
      state.value = state.value.copy(font = font)
    }

    override suspend fun setFontScale(scale: Float) {
      val clamped = scale.coerceIn(ReaderPreferences.SCALE_MIN, ReaderPreferences.SCALE_MAX)
      state.value = state.value.copy(fontScale = clamped)
    }

    override suspend fun setMargins(margins: ReaderMargins) {
      state.value = state.value.copy(margins = margins)
    }

    override suspend fun setAlignment(alignment: ReaderAlignment) {
      state.value = state.value.copy(alignment = alignment)
    }

    override suspend fun setLineSpacing(value: Float) {
      val clamped =
        value.coerceIn(ReaderPreferences.LINE_SPACING_MIN, ReaderPreferences.LINE_SPACING_MAX)
      state.value = state.value.copy(lineSpacing = clamped)
    }

    override suspend fun setParagraphSpacing(value: Float) {
      val clamped =
        value.coerceIn(
          ReaderPreferences.PARAGRAPH_SPACING_MIN,
          ReaderPreferences.PARAGRAPH_SPACING_MAX,
        )
      state.value = state.value.copy(paragraphSpacing = clamped)
    }

    override suspend fun setWordSpacing(value: Float) {
      val clamped =
        value.coerceIn(ReaderPreferences.WORD_SPACING_MIN, ReaderPreferences.WORD_SPACING_MAX)
      state.value = state.value.copy(wordSpacing = clamped)
    }

    override suspend fun setLetterSpacing(value: Float) {
      val clamped =
        value.coerceIn(ReaderPreferences.LETTER_SPACING_MIN, ReaderPreferences.LETTER_SPACING_MAX)
      state.value = state.value.copy(letterSpacing = clamped)
    }

    override suspend fun resetSpacing() {
      state.value =
        state.value.copy(
          lineSpacing = ReaderPreferences.LINE_SPACING_DEFAULT,
          paragraphSpacing = ReaderPreferences.PARAGRAPH_SPACING_DEFAULT,
          wordSpacing = ReaderPreferences.WORD_SPACING_DEFAULT,
          letterSpacing = ReaderPreferences.LETTER_SPACING_DEFAULT,
        )
    }
  }
}
