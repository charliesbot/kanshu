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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    mockk<Publication>(relaxed = true).also {
      val metadata = mockk<Metadata>(relaxed = true)
      every { metadata.title } returns title
      every { it.metadata } returns metadata
    }

  private fun newViewModel(): ReaderViewModel =
    ReaderViewModel(seriesId = 1, openBook = openBook, sync = sync, preferences = preferences)

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
  }
}
