package com.charliesbot.kanshu.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.charliesbot.kanshu.features.connection.ConnectionScreen
import com.charliesbot.kanshu.features.library.LibraryScreen
import com.charliesbot.kanshu.features.reader.ReaderScreen

// E-ink ghosts on any animation, so every NavDisplay transition is forced to None. PRD
// (docs/PRD.md) bans ripples, fades, and slides as a hard rule.
private val NoTransition: ContentTransform = EnterTransition.None togetherWith ExitTransition.None

@Composable
fun AppNavigation(start: NavKey) {
  val backStack = rememberNavBackStack(start)
  val activity = LocalActivity.current
  val popBack = {
    if (backStack.size > 1) {
      backStack.removeAt(backStack.lastIndex)
    }
  }

  // NavDisplay's onBack only fires when the back stack has something to pop. At the root,
  // back must finish the activity instead of being silently swallowed.
  BackHandler(enabled = backStack.size <= 1) { activity?.finish() }

  NavDisplay(
    backStack = backStack,
    onBack = { popBack() },
    transitionSpec = { NoTransition },
    popTransitionSpec = { NoTransition },
    predictivePopTransitionSpec = { NoTransition },
    entryProvider =
      entryProvider {
        entry<ConnectionRoute> { ConnectionScreen() }
        entry<LibraryRoute> {
          LibraryScreen(
            onItemClick = { item ->
              val next = ReaderRoute(item.id, item.title)
              if (backStack.lastOrNull() != next) backStack.add(next)
            }
          )
        }
        entry<ReaderRoute> { route -> ReaderScreen(seriesId = route.seriesId, title = route.title) }
      },
  )
}
