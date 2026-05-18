package com.charliesbot.kanshu

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi

// FragmentActivity (extends ComponentActivity, so all Compose plumbing still works) so we can
// host EpubNavigatorFragment via FragmentContainerView in the reader screen.
class MainActivity : FragmentActivity() {
  @OptIn(ExperimentalReadiumApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    // After process death the system reinstantiates any saved EpubNavigatorFragment via the
    // FragmentManager's factory before any Composable runs. Without a factory that knows how
    // to construct it (it has no no-arg constructor), restore crashes. The dummy factory
    // returns a placeholder; EpubNavigatorHost detects the stale fragment and removes it
    // before installing the real factory and re-adding the navigator. Must be set before
    // super.onCreate so it's in place during state restore.
    supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
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
