package com.charliesbot.kanshu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.connection.CredentialsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface StartupState {
  data object Loading : StartupState

  data object NeedsConnection : StartupState

  data object Ready : StartupState
}

class KanshuAppViewModel(credentialsRepository: CredentialsRepository) : ViewModel() {
  val startupState: StateFlow<StartupState> =
    credentialsRepository.credentials
      .map { if (it == null) StartupState.NeedsConnection else StartupState.Ready }
      .stateIn(viewModelScope, SharingStarted.Eagerly, StartupState.Loading)
}
