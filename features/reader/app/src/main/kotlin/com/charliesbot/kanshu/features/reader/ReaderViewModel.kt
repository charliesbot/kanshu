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

  data class Ready(
    val href: String,
    val resourceIndex: Int,
    val resourceCount: Int,
    val chapterHtml: String,
  ) : ReaderUiState

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
        var loadedPublication: Publication? = null
        try {
          val loadedBook =
            withContext(ioDispatcher) {
              when (val result = openBook(seriesId)) {
                ReaderResult.Error.NotFound -> LoadedReaderBook(ReaderUiState.Error.NotFound)
                ReaderResult.Error.ParseFailed -> LoadedReaderBook(ReaderUiState.Error.ParseFailed)
                ReaderResult.Error.ReadFailed -> LoadedReaderBook(ReaderUiState.Error.ReadFailed)
                is ReaderResult.Success -> {
                  loadedPublication = result.publication
                  val loadedState =
                    openReadableResourceAtOrAfter(result.publication, startIndex = 0)
                      ?: ReaderUiState.Error.ParseFailed
                  LoadedReaderBook(
                    uiState = loadedState,
                    publicationToKeep =
                      result.publication.takeIf { loadedState is ReaderUiState.Ready },
                  )
                }
              }
            }
          if (currentSeriesId == seriesId) {
            loadedBook.publicationToKeep?.let { keptPublication ->
              publication = keptPublication
              loadedPublication = null
            }
            _uiState.value = loadedBook.uiState
          }
        } finally {
          loadedPublication?.close()
        }
      }
  }

  fun openNextResource() {
    val openedPublication = publication ?: return
    val currentState = _uiState.value as? ReaderUiState.Ready ?: return

    openJob?.cancel()
    openJob =
      viewModelScope.launch {
        val loadedState =
          withContext(ioDispatcher) {
            openReadableResourceAtOrAfter(
              openedPublication = openedPublication,
              startIndex = currentState.resourceIndex,
            )
          }
        if (publication === openedPublication && loadedState != null) {
          _uiState.value = loadedState
        }
      }
  }

  override fun onCleared() {
    openJob?.cancel()
    publication?.close()
  }

  private suspend fun openReadableResourceAtOrAfter(
    openedPublication: Publication,
    startIndex: Int,
  ): ReaderUiState? {
    val readingOrder = openedPublication.readingOrder

    for (index in startIndex until readingOrder.size) {
      val link = readingOrder[index]
      val resource = openedPublication.get(link) ?: continue
      val bytes = resource.read().getOrNull() ?: return ReaderUiState.Error.ReadFailed
      val chapterHtml = ChapterHtmlExtractor.bodyHtml(bytes.toString(Charsets.UTF_8))
      if (ChapterHtmlExtractor.hasReadableText(chapterHtml)) {
        return ReaderUiState.Ready(
          href = link.href.toString(),
          resourceIndex = index + 1,
          resourceCount = openedPublication.readingOrder.size,
          chapterHtml = chapterHtml,
        )
      }
    }

    return null
  }
}

private data class LoadedReaderBook(
  val uiState: ReaderUiState,
  val publicationToKeep: Publication? = null,
)
