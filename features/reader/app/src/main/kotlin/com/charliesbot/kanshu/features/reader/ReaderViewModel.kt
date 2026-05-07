package com.charliesbot.kanshu.features.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.reader.BookHandle
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(private val seriesId: Int, private val openBook: OpenBookUseCase) :
  ViewModel() {
  private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
  val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

  private var bookHandle: BookHandle? = null

  init {
    viewModelScope.launch {
      _uiState.value =
        when (val result = openBook(seriesId)) {
          is ReaderResult.Success -> {
            bookHandle = result.handle
            ReaderUiState.Ready(
              title = result.handle.publication.metadata.title,
              factory = EpubNavigatorFactory(result.handle.publication),
            )
          }
          ReaderResult.Error.NotFound -> ReaderUiState.Error.NotFound
          ReaderResult.Error.ParseFailed -> ReaderUiState.Error.ParseFailed
          ReaderResult.Error.ReadFailed -> ReaderUiState.Error.ReadFailed
        }
    }
  }

  override fun onCleared() {
    bookHandle?.close()
    bookHandle = null
  }
}
