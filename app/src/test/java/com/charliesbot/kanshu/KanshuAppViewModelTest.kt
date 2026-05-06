package com.charliesbot.kanshu

import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.connection.KavitaCredentials
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KanshuAppViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val credentialsRepository: CredentialsRepository = mockk()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `null credentials emit NeedsConnection`() = runTest {
    every { credentialsRepository.credentials } returns MutableStateFlow(null)

    val viewModel = KanshuAppViewModel(credentialsRepository)

    val state = viewModel.startupState.firstOrNull { it != StartupState.Loading }
    assertEquals(StartupState.NeedsConnection, state)
  }

  @Test
  fun `non-null credentials emit Ready`() = runTest {
    every { credentialsRepository.credentials } returns
      MutableStateFlow(KavitaCredentials("https://kavita.example.com", "apikey"))

    val viewModel = KanshuAppViewModel(credentialsRepository)

    val state = viewModel.startupState.firstOrNull { it != StartupState.Loading }
    assertEquals(StartupState.Ready, state)
  }

  @Test
  fun `initial state is Loading`() = runTest {
    every { credentialsRepository.credentials } returns MutableStateFlow(null)

    val viewModel = KanshuAppViewModel(credentialsRepository)

    assertEquals(StartupState.Loading, viewModel.startupState.first())
  }
}
