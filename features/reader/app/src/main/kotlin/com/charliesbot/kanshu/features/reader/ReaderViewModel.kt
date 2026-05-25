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

  data class Ready(val href: String, val chapterHtml: String) : ReaderUiState

  sealed interface Error : ReaderUiState {
    data object NotFound : Error

    data object ParseFailed : Error

    data object ReadFailed : Error
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
        var openedPublication: Publication? = null
        var transferredPublication = false
        try {
          val loadedState =
            withContext(ioDispatcher) {
              when (val result = openBook(seriesId)) {
                ReaderResult.Error.NotFound -> ReaderUiState.Error.NotFound
                ReaderResult.Error.ParseFailed -> ReaderUiState.Error.ParseFailed
                ReaderResult.Error.ReadFailed -> ReaderUiState.Error.ReadFailed
                is ReaderResult.Success -> {
                  openedPublication = result.publication
                  openFirstChapter(result.publication)
                }
              }
            }
          if (currentSeriesId == seriesId) {
            if (loadedState is ReaderUiState.Ready) {
              publication = openedPublication
              transferredPublication = true
            }
            _uiState.value = loadedState
          }
        } finally {
          if (!transferredPublication) openedPublication?.close()
        }
      }
  }

  override fun onCleared() {
    openJob?.cancel()
    publication?.close()
  }

  private suspend fun openFirstChapter(openedPublication: Publication): ReaderUiState {
    if (openedPublication.readingOrder.isEmpty()) return ReaderUiState.Error.ParseFailed

    for (link in openedPublication.readingOrder) {
      val resource = openedPublication.get(link) ?: continue
      val bytes = resource.read().getOrNull() ?: return ReaderUiState.Error.ReadFailed
      val chapterHtml = ChapterHtmlExtractor.bodyHtml(bytes.toString(Charsets.UTF_8))
      if (ChapterHtmlExtractor.hasReadableText(chapterHtml)) {
        return ReaderUiState.Ready(href = link.href.toString(), chapterHtml = chapterHtml)
      }
    }

    return ReaderUiState.Error.ParseFailed
  }
}
