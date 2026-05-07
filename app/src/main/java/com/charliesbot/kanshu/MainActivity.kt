package com.charliesbot.kanshu

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme

// FragmentActivity (extends ComponentActivity, so all Compose plumbing still works) so we can
// host EpubNavigatorFragment via FragmentContainerView in the reader screen.
class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { KanshuTheme { KanshuApp() } }
  }
}
