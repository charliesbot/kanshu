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
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.AbsoluteUrl

// Hosts Readium's EpubNavigatorFragment inside Compose. The Fragment owns paginated rendering,
// tap zones, and chapter advancement; ReaderScreen drives Prev/Next via the supplied callback
// once the navigator is attached. publisherStyles=false strips the EPUB's own CSS so our
// preferences (and a future user stylesheet) own typography — Kindle-style.
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
    // The FragmentFactory must be set before commitNow so the FragmentManager instantiates
    // EpubNavigatorFragment with our publication, locator, and preferences instead of calling
    // its no-arg constructor (which throws).
    fragmentManager.fragmentFactory =
      factory.createFragmentFactory(
        initialLocator = null,
        // publisherStyles=false strips the EPUB's own CSS so typography is owned by us
        // (Kindle-style). columnCount=ONE forces a single-column page even on wide screens
        // (Readium defaults to two-up on landscape / wide tablets, which is wrong for e-ink).
        initialPreferences =
          EpubPreferences(publisherStyles = false, columnCount = ColumnCount.ONE),
        listener = NoopNavigatorListener,
      )
    if (fragmentManager.findFragmentByTag(tag) == null) {
      fragmentManager.commitNow {
        add(containerId, EpubNavigatorFragment::class.java, Bundle(), tag)
      }
    }
    (fragmentManager.findFragmentByTag(tag) as? EpubNavigatorFragment)?.let { onNavigatorReady(it) }
    onDispose {
      fragmentManager.findFragmentByTag(tag)?.let { fragment ->
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
