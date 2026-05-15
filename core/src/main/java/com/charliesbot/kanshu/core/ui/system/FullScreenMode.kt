package com.charliesbot.kanshu.core.ui.system

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// Hides the status and navigation bars while the calling composable is on screen and restores
// them on dispose. A swipe from a system-bar edge surfaces them transiently — the immersive
// reading mode Kindle uses, called for by the PRD's "zero persistent app UI" rule.
//
// `enabled = false` shows the system bars instead. Use this to suspend immersive mode while
// chrome (e.g. the reader overlay) is up, then flip it back on when chrome dismisses.
@Composable
fun FullScreenMode(enabled: Boolean = true) {
  val activity = LocalActivity.current ?: return
  DisposableEffect(activity, enabled) {
    val window = activity.window
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (enabled) {
      controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
      controller.show(WindowInsetsCompat.Type.systemBars())
    }
    onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
  }
}
