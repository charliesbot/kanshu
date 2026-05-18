package com.charliesbot.kanshu.features.reader

import android.os.Bundle
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl

// Hosts Readium's EpubNavigatorFragment inside Compose. The Fragment owns paginated rendering
// and chapter advancement; page turns come from ReaderScreen's swipe modifier plus the
// DirectionalNavigationAdapter tap zones installed below — there is no on-screen button UI.
//
// Typography is centralized in EpubTypography — defaults flow in via the factory configuration
// (set in ReaderViewModel), while initialPreferences and the fragment configuration (font-face
// declarations + servedAssets) flow in here. See docs/KINDLE_TYPOGRAPHY.md.
//
// The host writes to the activity's global supportFragmentManager.fragmentFactory — fine for
// our single-ReaderScreen-at-a-time setup, would clobber under concurrent fragment users.
@OptIn(ExperimentalReadiumApi::class)
@Composable
fun EpubNavigatorHost(
  factory: EpubNavigatorFactory,
  onNavigatorReady: (EpubNavigatorFragment) -> Unit,
  onCenterTap: () -> Unit,
  initialLocator: Locator? = null,
  modifier: Modifier = Modifier,
) {
  val activity = LocalActivity.current as? FragmentActivity ?: return
  val fragmentManager = activity.supportFragmentManager
  val containerId = rememberSaveable { View.generateViewId() }
  val tag = remember(containerId) { "epub_navigator_$containerId" }
  val onCenterTapState by rememberUpdatedState(onCenterTap)

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
        initialLocator = initialLocator,
        initialPreferences = EpubTypography.initialPreferences,
        listener = NoopNavigatorListener,
        configuration = EpubTypography.fragmentConfiguration,
      )
    fragmentManager.commitNow { add(containerId, EpubNavigatorFragment::class.java, Bundle(), tag) }
    val navigator = fragmentManager.findFragmentByTag(tag) as? EpubNavigatorFragment
    navigator?.let {
      // Readium 3.x doesn't wire tap-edge pagination by default; the adapter listens for taps
      // and calls go{Forward,Backward}. animatedTransition=false (its default) keeps the page
      // turn instant for e-ink. Edge threshold trimmed from the default 0.3 → 0.15 so the page-
      // turn strips are narrower; the remaining middle 70% goes to CenterTapListener.
      it.addInputListener(DirectionalNavigationAdapter(it, horizontalEdgeThresholdPercent = 0.15))
      // Center-zone tap (middle 70% horizontally, matching the adapter's edge threshold) reveals
      // the reader overlay. Returning false for edge taps lets DirectionalNavigationAdapter
      // handle them as page turns.
      it.addInputListener(CenterTapListener(it) { onCenterTapState() })
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
private class CenterTapListener(
  private val navigator: EpubNavigatorFragment,
  private val onCenterTap: () -> Unit,
) : InputListener {
  override fun onTap(event: TapEvent): Boolean {
    // event.point.x is in publicationView (the inner pager) coordinates — same frame
    // DirectionalNavigationAdapter uses for its edge math. Reading from navigator.view (the
    // fragment root) instead would drift if the root ever has padding/chrome around the pager.
    val width = navigator.publicationView.width
    if (width <= 0) return false
    val x = event.point.x
    val inCenter = x > width * 0.15f && x < width * 0.85f
    if (!inCenter) return false
    onCenterTap()
    return true
  }
}

@OptIn(ExperimentalReadiumApi::class)
private val NoopNavigatorListener =
  object : EpubNavigatorFragment.Listener {
    override fun onExternalLinkActivated(url: AbsoluteUrl) = Unit
  }
