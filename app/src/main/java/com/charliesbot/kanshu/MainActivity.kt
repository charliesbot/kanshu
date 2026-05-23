package com.charliesbot.kanshu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // E-ink panels do a partial refresh on every dispatched inset frame, so a single
    // immersive-mode toggle ghosts twice (mid-animation + end). DISPATCH_MODE_STOP makes the
    // view tree skip the slide and snap to the final insets, collapsing it to one refresh.
    ViewCompat.setWindowInsetsAnimationCallback(
      window.decorView,
      object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
        override fun onProgress(
          insets: WindowInsetsCompat,
          runningAnimations: List<WindowInsetsAnimationCompat>,
        ): WindowInsetsCompat = insets
      },
    )
    setContent { KanshuTheme { KanshuApp() } }
  }
}
