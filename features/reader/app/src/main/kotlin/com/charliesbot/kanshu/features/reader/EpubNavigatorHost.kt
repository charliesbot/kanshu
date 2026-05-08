package com.charliesbot.kanshu.features.reader

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.AbsoluteUrl

// Hosts Readium's EpubNavigatorFragment inside Compose. The Fragment owns paginated rendering,
// tap zones, and chapter advancement; ReaderScreen drives Prev/Next via the supplied callback
// once the navigator is attached. publisherStyles=false strips the EPUB's own CSS (Kindle-style
// typography ownership), columnCount=ONE forces single column on wide screens (Readium defaults
// to two-up on landscape, wrong for e-ink). The host writes to the activity's global
// supportFragmentManager.fragmentFactory — fine for our single-ReaderScreen-at-a-time setup,
// would clobber under concurrent fragment users.
@OptIn(ExperimentalReadiumApi::class)
@Composable
fun EpubNavigatorHost(
  factory: EpubNavigatorFactory,
  onNavigatorReady: (EpubNavigatorFragment) -> Unit,
  modifier: Modifier = Modifier,
) {
  val activity = LocalContext.current as? FragmentActivity ?: return
  val fragmentManager = activity.supportFragmentManager
  val containerId = rememberSaveable { View.generateViewId() }
  val tag = remember(containerId) { "epub_navigator_$containerId" }

  AndroidView(
    factory = { ctx -> FragmentContainerView(ctx).apply { id = containerId } },
    modifier = modifier,
  )

  DisposableEffect(factory, containerId) {
    // After process-death restore, MainActivity's dummy factory will have produced a stub
    // EpubNavigatorFragment under our tag. Drop it so the FragmentManager doesn't merge it
    // with the real navigator we're about to add.
    fragmentManager.findFragmentByTag(tag)?.let { stale ->
      fragmentManager.commitNow(allowStateLoss = true) { remove(stale) }
    }
    fragmentManager.fragmentFactory =
      factory.createFragmentFactory(
        initialLocator = null,
        initialPreferences =
          EpubPreferences(publisherStyles = false, columnCount = ColumnCount.ONE),
        listener = NoopNavigatorListener,
      )
    fragmentManager.commitNow { add(containerId, EpubNavigatorFragment::class.java, Bundle(), tag) }
    val navigator = fragmentManager.findFragmentByTag(tag) as? EpubNavigatorFragment
    navigator?.let {
      // Readium 3.x doesn't wire tap-edge pagination by default; the adapter listens for taps
      // and calls go{Forward,Backward}. animatedTransition=false (its default) keeps the page
      // turn instant for e-ink.
      it.addInputListener(DirectionalNavigationAdapter(it))
      onNavigatorReady(it)
    }
    onDispose {
      fragmentManager.findFragmentByTag(tag)?.let { fragment ->
        // allowStateLoss because dispose can fire after the activity has saved its instance
        // state (e.g. config change, process backgrounded). The navigator will be re-added on
        // re-mount; we don't need its in-memory state to survive.
        fragmentManager.commit(allowStateLoss = true) { remove(fragment) }
      }
    }
  }
}

@OptIn(ExperimentalReadiumApi::class)
private val NoopNavigatorListener =
  object : EpubNavigatorFragment.Listener {
    override fun onExternalLinkActivated(url: AbsoluteUrl) = Unit
  }
