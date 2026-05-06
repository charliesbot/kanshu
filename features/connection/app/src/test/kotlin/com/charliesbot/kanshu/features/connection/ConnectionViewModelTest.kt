package com.charliesbot.kanshu.features.connection

import com.charliesbot.kanshu.core.connection.ConnectionRepository
import com.charliesbot.kanshu.core.connection.ConnectionTestResult
import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.connection.KavitaCredentials
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val connectionRepository: ConnectionRepository = mockk()
  private val credentialsRepository: CredentialsRepository = mockk(relaxed = true)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `successful test saves credentials`() = runTest {
    coEvery { connectionRepository.testConnection(any(), any()) } returns
      ConnectionTestResult.Ok(serverVersion = "0.7.14")
    val viewModel = ConnectionViewModel(connectionRepository, credentialsRepository)

    viewModel.onBaseUrlChange("https://kavita.example.com")
    viewModel.onApiKeyChange("apikey123")
    viewModel.onTest()
    advanceUntilIdle()

    coVerify(exactly = 1) {
      credentialsRepository.save(KavitaCredentials("https://kavita.example.com", "apikey123"))
    }
  }

  @Test
  fun `failed test does not save credentials`() = runTest {
    coEvery { connectionRepository.testConnection(any(), any()) } returns
      ConnectionTestResult.Unauthorized
    val viewModel = ConnectionViewModel(connectionRepository, credentialsRepository)

    viewModel.onBaseUrlChange("https://kavita.example.com")
    viewModel.onApiKeyChange("apikey123")
    viewModel.onTest()
    advanceUntilIdle()

    coVerify(exactly = 0) { credentialsRepository.save(any()) }
  }

  @Test
  fun `inputs are trimmed before save`() = runTest {
    coEvery { connectionRepository.testConnection(any(), any()) } returns
      ConnectionTestResult.Ok(serverVersion = null)
    val viewModel = ConnectionViewModel(connectionRepository, credentialsRepository)

    viewModel.onBaseUrlChange("  https://kavita.example.com  ")
    viewModel.onApiKeyChange("  apikey123  ")
    viewModel.onTest()
    advanceUntilIdle()

    coVerify(exactly = 1) {
      credentialsRepository.save(KavitaCredentials("https://kavita.example.com", "apikey123"))
    }
  }
}
