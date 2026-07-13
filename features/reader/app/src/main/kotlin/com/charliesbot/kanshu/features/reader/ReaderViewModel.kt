package com.charliesbot.kanshu.features.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderMargins
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.reader.ReaderPreferencesRepository
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import com.charliesbot.kanshu.navigator.ReaderResourceLoader
import com.charliesbot.kanshu.navigator.model.ParseDiagnostics
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication

private const val TAG = "ReaderViewModel"

// Placeholder page index meaning "last page of the chapter". Set when navigating backward into a
// chapter whose page count is not known yet; onPageCount clamps it to the real last page.
private const val LAST_PAGE_SENTINEL = Int.MAX_VALUE

sealed interface ReaderUiState {
  data object Loading : ReaderUiState

  data class Reading(
    val spineIndex: Int,
    val document: ReaderDocument,
    val diagnostics: ParseDiagnostics,
  ) : ReaderUiState

  sealed interface Error : ReaderUiState {
    data object NotFound : Error

    data object OpenFailed : Error
  }
}

class ReaderViewModel(
  private val openBook: OpenBookUseCase,
  private val preferencesRepository: ReaderPreferencesRepository,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
  private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
  val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

  /**
   * Applied reader typography. Starts from [ReaderPreferences] defaults so the first frame renders
   * well with no configuration; the repository emits the persisted values (which also default
   * field-by-field for anything never set).
   */
  val preferences: StateFlow<ReaderPreferences> =
    preferencesRepository.preferences.stateIn(
      viewModelScope,
      SharingStarted.Eagerly,
      ReaderPreferences(),
    )

  fun setFont(font: ReaderFont) {
    viewModelScope.launch { preferencesRepository.setFont(font) }
  }

  fun setFontScale(scale: Float) {
    viewModelScope.launch { preferencesRepository.setFontScale(scale) }
  }

  fun setMargins(margins: ReaderMargins) {
    viewModelScope.launch { preferencesRepository.setMargins(margins) }
  }

  fun setAlignment(alignment: ReaderAlignment) {
    viewModelScope.launch { preferencesRepository.setAlignment(alignment) }
  }

  fun setLineSpacing(value: Float) {
    viewModelScope.launch { preferencesRepository.setLineSpacing(value) }
  }

  fun setParagraphSpacing(value: Float) {
    viewModelScope.launch { preferencesRepository.setParagraphSpacing(value) }
  }

  fun setWordSpacing(value: Float) {
    viewModelScope.launch { preferencesRepository.setWordSpacing(value) }
  }

  fun setLetterSpacing(value: Float) {
    viewModelScope.launch { preferencesRepository.setLetterSpacing(value) }
  }

  fun resetSpacing() {
    viewModelScope.launch { preferencesRepository.resetSpacing() }
  }

  private val _currentPage = MutableStateFlow(0)
  val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

  private val _pageCount = MutableStateFlow(0)
  val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

  private val _resourceLoader = MutableStateFlow<ReaderResourceLoader?>(null)
  val resourceLoader: StateFlow<ReaderResourceLoader?> = _resourceLoader.asStateFlow()

  private enum class LandingPage {
    Start,
    End,
  }

  private var openJob: Job? = null
  private var spineJob: Job? = null
  private var currentSeriesId: Int? = null
  private var publication: Publication? = null
  private var currentSpineIndex = -1
  // Chapter reentry (e.g. paging back across a boundary) must not re-parse the spine item.
  private val spineItemCache = mutableMapOf<Int, SpineItem>()
  private var stylesheets: PublicationStylesheets? = null

  private fun lastPageIndex(): Int = (_pageCount.value - 1).coerceAtLeast(0)

  fun open(seriesId: Int) {
    if (currentSeriesId == seriesId) return
    currentSeriesId = seriesId
    _uiState.value = ReaderUiState.Loading
    _currentPage.value = 0
    _pageCount.value = 0
    openJob?.cancel()
    spineJob?.cancel()
    spineJob = null
    publication?.close()
    publication = null
    _resourceLoader.value = null
    currentSpineIndex = -1
    spineItemCache.clear()
    stylesheets = null

    openJob = viewModelScope.launch {
      Log.d(TAG, "open($seriesId): loading")
      val result = withContext(ioDispatcher) { openBook(seriesId) }
      when (result) {
        is ReaderResult.Success -> {
          publication = result.publication
          _resourceLoader.value = PublicationResourceLoader(result.publication)
          stylesheets = PublicationStylesheets(result.publication)
          Log.d(
            TAG,
            "open($seriesId): publication opened, spine=${result.publication.readingOrder.size}",
          )
          val spineItem =
            withContext(ioDispatcher) {
              result.publication.readNextSpineItem(afterSpineIndex = -1, stylesheets)
            }
          if (spineItem == null) {
            failOpen("open($seriesId): no spine item → OpenFailed")
            return@launch
          }
          spineItemCache[spineItem.spineIndex] = spineItem
          currentSpineIndex = spineItem.spineIndex
          Log.d(TAG, "open($seriesId): Reading with ${spineItem.document.blocks.size} blocks")
          _uiState.value =
            ReaderUiState.Reading(
              spineIndex = spineItem.spineIndex,
              document = spineItem.document,
              diagnostics = spineItem.diagnostics,
            )
        }
        ReaderResult.Error.NotFound -> {
          Log.d(TAG, "open($seriesId): NotFound")
          _uiState.value = ReaderUiState.Error.NotFound
        }
        ReaderResult.Error.ParseFailed -> {
          failOpen("open($seriesId): ParseFailed → OpenFailed")
        }
        ReaderResult.Error.ReadFailed -> {
          failOpen("open($seriesId): ReadFailed → OpenFailed")
        }
      }
    }
  }

  fun onPageCount(spineIndex: Int, count: Int) {
    val reading = _uiState.value as? ReaderUiState.Reading
    if (reading?.spineIndex != spineIndex) {
      Log.d(TAG, "onPageCount($count) ignored for stale spine[$spineIndex]")
      return
    }
    Log.d(TAG, "onPageCount($count)")
    _pageCount.value = count
    _currentPage.update { page -> page.coerceIn(0, lastPageIndex()) }
  }

  fun onLayoutFailed() {
    failOpen("onLayoutFailed → OpenFailed")
  }

  private fun failOpen(message: String) {
    Log.d(TAG, message)
    _uiState.value = ReaderUiState.Error.OpenFailed
  }

  fun nextPage() {
    if (_pageCount.value == 0) {
      Log.d(TAG, "nextPage ignored while page count is unknown")
      return
    }
    if (_currentPage.value >= lastPageIndex()) {
      openSpineItem(currentSpineIndex + 1, LandingPage.Start)
      return
    }
    _currentPage.update { page ->
      val next = (page + 1).coerceAtMost(lastPageIndex())
      Log.d(TAG, "nextPage: $page → $next (of ${_pageCount.value})")
      next
    }
  }

  fun previousPage() {
    if (_pageCount.value == 0) {
      Log.d(TAG, "previousPage ignored while page count is unknown")
      return
    }
    if (_currentPage.value <= 0) {
      if (currentSpineIndex > 0) {
        openSpineItem(currentSpineIndex - 1, LandingPage.End)
      } else {
        Log.d(TAG, "previousPage: already at first spine item")
      }
      return
    }
    _currentPage.update { page ->
      val previous = page - 1
      Log.d(TAG, "previousPage: $page → $previous (of ${_pageCount.value})")
      previous
    }
  }

  /**
   * Navigates to the spine item a publication-internal link points at. Fragments resolve to the
   * chapter start for now — anchor-to-page mapping is a later slice. Unresolvable hrefs and
   * same-chapter links are ignored.
   */
  fun openLink(href: String) {
    val path = href.substringBefore('#')
    val currentPublication = publication ?: return
    val target =
      currentPublication.readingOrder.indexOfFirst { link ->
        link.url().path?.trimStart('/') == path
      }
    if (target == -1) {
      Log.d(TAG, "openLink: no spine item for $href")
      return
    }
    if (target == currentSpineIndex) {
      Log.d(TAG, "openLink: already on spine[$target], fragment navigation not yet supported")
      return
    }
    openSpineItem(target, LandingPage.Start)
  }

  private fun openSpineItem(targetSpineIndex: Int, landing: LandingPage) {
    if (spineJob?.isActive == true) return
    val currentPublication = publication ?: return
    val startingSpineIndex = currentSpineIndex
    spineJob = viewModelScope.launch {
      try {
        val item =
          spineItemCache[targetSpineIndex]
            ?: withContext(ioDispatcher) {
              currentPublication.readSpineItemAt(targetSpineIndex, stylesheets)
            }
        if (publication !== currentPublication || currentSpineIndex != startingSpineIndex) {
          Log.d(TAG, "openSpineItem: ignored stale open of spine[$targetSpineIndex]")
          return@launch
        }
        if (item == null) {
          Log.d(TAG, "openSpineItem: spine[$targetSpineIndex] unavailable")
          return@launch
        }
        spineItemCache[item.spineIndex] = item
        currentSpineIndex = item.spineIndex
        _pageCount.value = 0
        _currentPage.value = if (landing == LandingPage.End) LAST_PAGE_SENTINEL else 0
        Log.d(TAG, "openSpineItem: opened spine[${item.spineIndex}] landing=$landing")
        _uiState.value =
          ReaderUiState.Reading(
            spineIndex = item.spineIndex,
            document = item.document,
            diagnostics = item.diagnostics,
          )
      } finally {
        val runningJob = currentCoroutineContext()[Job]
        if (spineJob === runningJob) {
          spineJob = null
        }
      }
    }
  }

  override fun onCleared() {
    openJob?.cancel()
    spineJob?.cancel()
    publication?.close()
  }
}
