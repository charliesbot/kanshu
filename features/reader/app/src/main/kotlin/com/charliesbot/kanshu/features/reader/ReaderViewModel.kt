package com.charliesbot.kanshu.features.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import com.charliesbot.kanshu.navigator.model.ParseDiagnostics
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication

private const val TAG = "ReaderViewModel"

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
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
  private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
  val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

  private val _currentPage = MutableStateFlow(0)
  val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

  private val _pageCount = MutableStateFlow(0)
  val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

  private var openJob: Job? = null
  private var nextSpineJob: Job? = null
  private var currentSeriesId: Int? = null
  private var publication: Publication? = null
  private var currentSpineIndex = -1

  private fun lastPageIndex(): Int = (_pageCount.value - 1).coerceAtLeast(0)

  fun open(seriesId: Int) {
    if (currentSeriesId == seriesId) return
    currentSeriesId = seriesId
    _uiState.value = ReaderUiState.Loading
    _currentPage.value = 0
    _pageCount.value = 0
    openJob?.cancel()
    nextSpineJob?.cancel()
    nextSpineJob = null
    publication?.close()
    publication = null
    currentSpineIndex = -1

    openJob =
      viewModelScope.launch {
        Log.d(TAG, "open($seriesId): loading")
        val result = withContext(ioDispatcher) { openBook(seriesId) }
        when (result) {
          is ReaderResult.Success -> {
            publication = result.publication
            Log.d(
              TAG,
              "open($seriesId): publication opened, spine=${result.publication.readingOrder.size}",
            )
            val spineItem =
              withContext(ioDispatcher) {
                result.publication.readNextSpineItem(afterSpineIndex = -1)
              }
            if (spineItem == null) {
              failOpen("open($seriesId): no spine item → OpenFailed")
              return@launch
            }
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
      openNextSpineItem()
      return
    }
    _currentPage.update { page ->
      val next = (page + 1).coerceAtMost(lastPageIndex())
      Log.d(TAG, "nextPage: $page → $next (of ${_pageCount.value})")
      next
    }
  }

  private fun openNextSpineItem() {
    if (nextSpineJob?.isActive == true) return
    val currentPublication = publication ?: return
    val startingSpineIndex = currentSpineIndex
    nextSpineJob =
      viewModelScope.launch {
        try {
          val nextItem =
            withContext(ioDispatcher) {
              currentPublication.readNextSpineItem(afterSpineIndex = startingSpineIndex)
            }
          if (publication !== currentPublication || currentSpineIndex != startingSpineIndex) {
            Log.d(TAG, "nextPage: ignored stale spine open after spine[$startingSpineIndex]")
            return@launch
          }
          if (nextItem == null) {
            Log.d(TAG, "nextPage: no spine item after spine[$currentSpineIndex]")
            return@launch
          }
          currentSpineIndex = nextItem.spineIndex
          _currentPage.value = 0
          _pageCount.value = 0
          Log.d(TAG, "nextPage: opened spine[${nextItem.spineIndex}]")
          _uiState.value =
            ReaderUiState.Reading(
              spineIndex = nextItem.spineIndex,
              document = nextItem.document,
              diagnostics = nextItem.diagnostics,
            )
        } finally {
          val runningJob = currentCoroutineContext()[Job]
          if (nextSpineJob === runningJob) {
            nextSpineJob = null
          }
        }
      }
  }

  fun previousPage() {
    _currentPage.update { page ->
      val previous = (page - 1).coerceAtLeast(0)
      Log.d(TAG, "previousPage: $page → $previous (of ${_pageCount.value})")
      previous
    }
  }

  override fun onCleared() {
    openJob?.cancel()
    nextSpineJob?.cancel()
    publication?.close()
  }
}
