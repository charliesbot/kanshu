package com.charliesbot.kanshu.features.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.library.LibraryItem
import com.charliesbot.kanshu.core.library.LibraryResult
import com.charliesbot.kanshu.core.library.usecase.LoadLibraryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

class LibraryViewModel(private val loadLibrary: LoadLibraryUseCase) : ViewModel() {
  private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
  val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch { _uiState.value = loadLibrary().toUiState() }
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
