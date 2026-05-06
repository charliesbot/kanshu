package com.charliesbot.kanshu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.navigation.AppNavigation
import com.charliesbot.kanshu.navigation.ConnectionRoute
import com.charliesbot.kanshu.navigation.LibraryRoute
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun KanshuApp(viewModel: KanshuAppViewModel = koinViewModel()) {
  val state by viewModel.startupState.collectAsState()
  when (state) {
    StartupState.Loading -> KanshuScaffold {}
    StartupState.NeedsConnection -> AppNavigation(start = ConnectionRoute)
    StartupState.Ready -> AppNavigation(start = LibraryRoute)
  }
}
