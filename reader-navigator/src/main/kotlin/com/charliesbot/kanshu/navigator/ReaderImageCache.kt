package com.charliesbot.kanshu.navigator

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.charliesbot.kanshu.navigator.engine.ImageBounds
import com.charliesbot.kanshu.navigator.engine.PageEntry
import com.charliesbot.kanshu.navigator.engine.ReaderPage

/**
 * Publication-scoped image cache. Hrefs are publication-root-relative, so entries stay valid across
 * chapters — hoist one instance per book above any per-chapter recomposition boundary (e.g. a
 * `key(spineIndex)` wrapper) so chapter changes reuse decoded bitmaps and bounds.
 *
 * Both maps are read and written on the composition's context only; decode work happens on IO and
 * merges back on the caller's context.
 *
 * A null bitmap value marks a failed decode so it is not retried.
 */
@Stable
class ReaderImageCache {
  internal val bounds: MutableMap<String, ImageBounds> = mutableMapOf()
  internal val bitmaps: SnapshotStateMap<String, Bitmap?> = mutableStateMapOf()
}

@Composable fun rememberReaderImageCache(): ReaderImageCache = remember { ReaderImageCache() }

/** Image entries on this page whose href has neither a decoded bitmap nor a recorded failure. */
internal fun ReaderPage.pendingImageEntries(decidedHrefs: Set<String>): List<PageEntry.Image> =
  entries
    .filterIsInstance<PageEntry.Image>()
    .filter { it.resourceHref.isNotBlank() && it.resourceHref !in decidedHrefs }
    .distinctBy { it.resourceHref }
