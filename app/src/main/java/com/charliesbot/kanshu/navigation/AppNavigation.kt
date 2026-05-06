package com.charliesbot.kanshu.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.charliesbot.kanshu.features.connection.ConnectionScreen
import com.charliesbot.kanshu.features.library.LibraryScreen

@Composable
fun AppNavigation(start: NavKey) {
  val backStack = rememberNavBackStack(start)

  NavDisplay(
    backStack = backStack,
    onBack = {
      if (backStack.size > 1) {
        backStack.removeAt(backStack.lastIndex)
      }
    },
    entryProvider =
      entryProvider {
        entry<ConnectionRoute> { ConnectionScreen() }
        entry<LibraryRoute> { LibraryScreen() }
      },
  )
}
