package com.charliesbot.kanshu.core.ui.system

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// Hides the status and navigation bars while the calling composable is on screen and restores
// them on dispose. A swipe from a system-bar edge surfaces them transiently — the immersive
// reading mode Kindle uses, called for by the PRD's "zero persistent app UI" rule.
@Composable
fun FullScreenMode() {
  val activity = LocalActivity.current ?: return
  DisposableEffect(activity) {
    val window = activity.window
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
    onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
  }
}
