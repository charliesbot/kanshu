package com.charliesbot.kanshu.features.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

  data class Reading(val document: ReaderDocument) : ReaderUiState

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
  private var currentSeriesId: Int? = null
  private var publication: Publication? = null

  private fun lastPageIndex(): Int = (_pageCount.value - 1).coerceAtLeast(0)

  fun open(seriesId: Int) {
    if (currentSeriesId == seriesId) return
    currentSeriesId = seriesId
    _uiState.value = ReaderUiState.Loading
    _currentPage.value = 0
    _pageCount.value = 0
    openJob?.cancel()
    publication?.close()
    publication = null

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
            val document =
              withContext(ioDispatcher) { result.publication.readFirstReadableChapter() }
            if (document == null) {
              failOpen("open($seriesId): no readable chapter → OpenFailed")
              return@launch
            }
            Log.d(TAG, "open($seriesId): Reading with ${document.blocks.size} blocks")
            _uiState.value = ReaderUiState.Reading(document)
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

  fun onPageCount(count: Int) {
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
    _currentPage.update { page ->
      val next = (page + 1).coerceAtMost(lastPageIndex())
      Log.d(TAG, "nextPage: $page → $next (of ${_pageCount.value})")
      next
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
    publication?.close()
  }
}
