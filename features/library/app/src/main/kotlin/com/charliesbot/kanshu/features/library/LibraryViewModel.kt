package com.charliesbot.kanshu.features.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.library.DownloadState
import com.charliesbot.kanshu.core.library.LibraryItem
import com.charliesbot.kanshu.core.library.LibraryResult
import com.charliesbot.kanshu.core.library.usecase.DeleteDownloadUseCase
import com.charliesbot.kanshu.core.library.usecase.DownloadBookUseCase
import com.charliesbot.kanshu.core.library.usecase.LoadLibraryUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
  data object Loading : LibraryUiState

  data object Empty : LibraryUiState

  data object NoCredentials : LibraryUiState

  data class Loaded(val items: List<LibraryItem>) : LibraryUiState

  sealed interface Error : LibraryUiState {
    data object Unauthorized : Error

    data object Network : Error

    data object UnexpectedResponse : Error

    data object Unknown : Error
  }
}

class LibraryViewModel(
  private val loadLibrary: LoadLibraryUseCase,
  private val downloadBook: DownloadBookUseCase,
  private val deleteDownload: DeleteDownloadUseCase,
) : ViewModel() {
  private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
  val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

  // The book whose options dialog is open; null when no dialog is shown.
  private val _options = MutableStateFlow<LibraryItem?>(null)
  val options: StateFlow<LibraryItem?> = _options.asStateFlow()

  // One-shot navigate-to-reader events. The screen forwards these to the navigation callback.
  // SharedFlow with a small buffer avoids dropping a tap that races with an in-progress collect
  // (e.g., during configuration change). Not state — replay would re-navigate on screen rotation.
  private val _navigate = MutableSharedFlow<LibraryItem>(extraBufferCapacity = 1)
  val navigate: SharedFlow<LibraryItem> = _navigate.asSharedFlow()

  init {
    viewModelScope.launch {
      loadLibrary().collect { result -> _uiState.value = result.toUiState() }
    }
  }

  fun onItemTap(item: LibraryItem) {
    when (item.downloadState) {
      DownloadState.NotDownloaded -> downloadBook(item)
      is DownloadState.Downloading -> Unit
      DownloadState.Downloaded -> _navigate.tryEmit(item)
    }
  }

  fun onItemLongPress(item: LibraryItem) {
    if (item.downloadState == DownloadState.Downloaded) _options.value = item
  }

  fun dismissOptions() {
    _options.value = null
  }

  fun confirmDeleteDownload() {
    val item = _options.value ?: return
    deleteDownload(item.id)
    _options.value = null
  }
}

private fun LibraryResult.toUiState(): LibraryUiState =
  when (this) {
    is LibraryResult.Success -> LibraryUiState.Loaded(items)
    LibraryResult.Empty -> LibraryUiState.Empty
    LibraryResult.NoCredentials -> LibraryUiState.NoCredentials
    LibraryResult.Error.Unauthorized -> LibraryUiState.Error.Unauthorized
    LibraryResult.Error.Network -> LibraryUiState.Error.Network
    LibraryResult.Error.UnexpectedResponse -> LibraryUiState.Error.UnexpectedResponse
    LibraryResult.Error.Unknown -> LibraryUiState.Error.Unknown
  }
