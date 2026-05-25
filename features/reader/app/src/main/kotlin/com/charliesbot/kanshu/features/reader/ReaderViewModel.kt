package com.charliesbot.kanshu.features.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication

sealed interface ReaderUiState {
  data object Loading : ReaderUiState

  data object Ready : ReaderUiState

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

  private var openJob: Job? = null
  private var currentSeriesId: Int? = null
  private var publication: Publication? = null

  fun open(seriesId: Int) {
    if (currentSeriesId == seriesId) return
    currentSeriesId = seriesId
    _uiState.value = ReaderUiState.Loading
    openJob?.cancel()
    publication?.close()
    publication = null

    openJob =
      viewModelScope.launch {
        val result = withContext(ioDispatcher) { openBook(seriesId) }
        when (result) {
          is ReaderResult.Success -> {
            publication = result.publication
            _uiState.value = ReaderUiState.Ready
          }
          ReaderResult.Error.NotFound -> _uiState.value = ReaderUiState.Error.NotFound
          ReaderResult.Error.ParseFailed -> _uiState.value = ReaderUiState.Error.OpenFailed
          ReaderResult.Error.ReadFailed -> _uiState.value = ReaderUiState.Error.OpenFailed
        }
      }
  }

  override fun onCleared() {
    openJob?.cancel()
    publication?.close()
  }
}
