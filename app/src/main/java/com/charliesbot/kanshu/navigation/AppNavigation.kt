package com.charliesbot.kanshu.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.charliesbot.kanshu.features.home.HomeScreen

@Composable
fun AppNavigation() {
  val backStack = rememberNavBackStack(HomeRoute)

  NavDisplay(
    backStack = backStack,
    onBack = {
      if (backStack.size > 1) {
        backStack.removeAt(backStack.lastIndex)
      }
    },
    entryProvider = entryProvider { entry<HomeRoute> { HomeScreen() } },
  )
}
