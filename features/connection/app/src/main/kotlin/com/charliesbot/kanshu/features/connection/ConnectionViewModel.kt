package com.charliesbot.kanshu.features.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.connection.ConnectionRepository
import com.charliesbot.kanshu.core.connection.ConnectionTestResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectionUiState(
  val baseUrl: String = "",
  val apiKey: String = "",
  val status: TestStatus = TestStatus.Idle,
) {
  val canTest: Boolean
    get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && status !is TestStatus.Testing
}

sealed interface TestStatus {
  data object Idle : TestStatus

  data object Testing : TestStatus

  data class Success(val serverVersion: String?) : TestStatus

  data class Error(val message: String) : TestStatus
}

class ConnectionViewModel(private val repository: ConnectionRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(ConnectionUiState())
  val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

  private var inFlight: Job? = null

  fun onBaseUrlChange(value: String) {
    cancelInFlight()
    _uiState.update { it.copy(baseUrl = value, status = TestStatus.Idle) }
  }

  fun onApiKeyChange(value: String) {
    cancelInFlight()
    _uiState.update { it.copy(apiKey = value, status = TestStatus.Idle) }
  }

  fun onTest() {
    if (inFlight?.isActive == true) return
    val state = _uiState.value
    if (!state.canTest) return
    _uiState.update { it.copy(status = TestStatus.Testing) }
    inFlight =
      viewModelScope.launch {
        val result = repository.testConnection(state.baseUrl.trim(), state.apiKey.trim())
        _uiState.update { it.copy(status = result.toStatus()) }
      }
  }

  private fun cancelInFlight() {
    inFlight?.cancel()
    inFlight = null
  }
}

private fun ConnectionTestResult.toStatus(): TestStatus =
  when (this) {
    is ConnectionTestResult.Ok -> TestStatus.Success(serverVersion)
    ConnectionTestResult.InvalidUrl -> TestStatus.Error("Invalid URL. Use http(s)://host[:port].")
    ConnectionTestResult.Unauthorized -> TestStatus.Error("Invalid API key.")
    ConnectionTestResult.UnexpectedResponse ->
      TestStatus.Error("Unexpected response. Check the base URL points at the API root.")
    ConnectionTestResult.NetworkError -> TestStatus.Error("Could not reach server.")
    is ConnectionTestResult.Unexpected -> TestStatus.Error(message)
  }
