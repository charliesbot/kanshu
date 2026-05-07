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

private enum class PendingPage {
  First,
  Last,
}

class ReaderViewModel(private val seriesId: Int, private val openBook: OpenBookUseCase) :
  ViewModel() {
  private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
  val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

  private var bookHandle: BookHandle? = null
  // Tracks the active chapter read so a rapid Prev/Next sequence cancels the in-flight read
  // and the most recent tap wins instead of the slowest read.
  private var chapterJob: Job? = null
  // Set when crossing a chapter boundary so onPageCountReported lands on the right page
  // (First → page 0 after Next; Last → final page after Prev). Null on resize / initial load.
  private var pendingPage: PendingPage? = PendingPage.First

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
    val count = state.pageCount
    if (count != null && state.currentPageIndex + 1 < count) {
      _uiState.value = state.copy(currentPageIndex = state.currentPageIndex + 1)
    } else if (state.currentChapterIndex + 1 < state.chapterCount) {
      pendingPage = PendingPage.First
      advanceChapter(state.currentChapterIndex + 1)
    }
  }

  fun goPrev() {
    val state = _uiState.value as? ReaderUiState.Ready ?: return
    if (state.currentPageIndex > 0) {
      _uiState.value = state.copy(currentPageIndex = state.currentPageIndex - 1)
    } else if (state.currentChapterIndex > 0) {
      pendingPage = PendingPage.Last
      advanceChapter(state.currentChapterIndex - 1)
    }
  }

  // Called from the WebView's JS bridge after each chapter is laid out (and on resize). Hops
  // back onto viewModelScope so pendingPage and _uiState are only touched from one dispatcher.
  fun onPageCountReported(count: Int) {
    viewModelScope.launch {
      val state = _uiState.value as? ReaderUiState.Ready ?: return@launch
      val pending = pendingPage
      val target =
        when (pending) {
          PendingPage.First -> 0
          PendingPage.Last -> (count - 1).coerceAtLeast(0)
          null -> state.currentPageIndex.coerceIn(0, count - 1)
        }
      pendingPage = null
      _uiState.value = state.copy(pageCount = count, currentPageIndex = target)
    }
  }

  private fun advanceChapter(index: Int) {
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
        currentChapterIndex = index,
        currentHtml = ChapterStyler.style(bytes),
      )
  }

  override fun onCleared() {
    chapterJob?.cancel()
    bookHandle?.close()
    bookHandle = null
  }
}
