package com.charliesbot.kanshu.features.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.reader.BookHandle
import com.charliesbot.kanshu.core.reader.ChapterStyler
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReaderViewModel(private val seriesId: Int, private val openBook: OpenBookUseCase) :
  ViewModel() {
  private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
  val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

  private var bookHandle: BookHandle? = null
  // Tracks the active chapter read so a rapid Prev/Next sequence cancels the in-flight read
  // and the most recent tap wins instead of the slowest read.
  private var chapterJob: Job? = null

  init {
    viewModelScope.launch {
      when (val result = openBook(seriesId)) {
        is ReaderResult.Success -> {
          bookHandle = result.handle
          loadChapter(0)
        }
        ReaderResult.Error.NotFound -> _uiState.value = ReaderUiState.Error.NotFound
        ReaderResult.Error.ParseFailed -> _uiState.value = ReaderUiState.Error.ParseFailed
        ReaderResult.Error.ReadFailed -> _uiState.value = ReaderUiState.Error.ReadFailed
      }
    }
  }

  fun goNext() {
    val state = _uiState.value as? ReaderUiState.Ready ?: return
    if (state.currentIndex + 1 < state.chapterCount) advanceTo(state.currentIndex + 1)
  }

  fun goPrev() {
    val state = _uiState.value as? ReaderUiState.Ready ?: return
    if (state.currentIndex > 0) advanceTo(state.currentIndex - 1)
  }

  private fun advanceTo(index: Int) {
    chapterJob?.cancel()
    chapterJob = viewModelScope.launch { loadChapter(index) }
  }

  private suspend fun loadChapter(index: Int) {
    val handle = bookHandle ?: return
    val bytes = handle.chapterBytes(index)
    if (bytes == null) {
      _uiState.value = ReaderUiState.Error.ReadFailed
      return
    }
    _uiState.value =
      ReaderUiState.Ready(
        title = handle.title,
        chapterCount = handle.chapterCount,
        currentIndex = index,
        currentHtml = ChapterStyler.style(bytes),
      )
  }

  override fun onCleared() {
    chapterJob?.cancel()
    bookHandle?.close()
    bookHandle = null
  }
}
