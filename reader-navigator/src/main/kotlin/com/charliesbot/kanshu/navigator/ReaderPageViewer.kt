package com.charliesbot.kanshu.navigator

import android.graphics.Typeface
import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.navigator.engine.BlockStyleResolver
import com.charliesbot.kanshu.navigator.engine.ReaderLayoutEngine
import com.charliesbot.kanshu.navigator.engine.ReaderPage
import com.charliesbot.kanshu.navigator.engine.ReaderViewport
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.render.ReaderPageCanvasView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ReaderPageViewer"

@Composable
fun ReaderPageViewer(
  document: ReaderDocument,
  preferences: ReaderPreferences,
  currentPage: Int,
  onPageCount: (Int) -> Unit,
  onLayoutFailed: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val typeface = remember(preferences.font) { loadReaderTypeface(context.assets, preferences.font) }

  BoxWithConstraints(modifier = modifier) {
    val viewport = rememberReaderViewport(maxWidth, maxHeight)
    var pages by remember { mutableStateOf<List<ReaderPage>?>(null) }
    val styleResolver =
      remember(preferences, viewport.density, typeface) {
        BlockStyleResolver(preferences, typeface, viewport.density)
      }

    LaunchedReaderLayout(
      document = document,
      preferences = preferences,
      viewport = viewport,
      typeface = typeface,
      styleResolver = styleResolver,
      onPages = { laidOut -> pages = laidOut },
      onPageCount = onPageCount,
      onLayoutFailed = onLayoutFailed,
    )

    val page = pages?.getOrNull(currentPage.coerceAtLeast(0))
    if (page != null) {
      ReaderPageAndroidView(
        page = page,
        currentPage = currentPage,
        styleResolver = styleResolver,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

@Composable
private fun rememberReaderViewport(width: Dp, height: Dp): ReaderViewport {
  val density = LocalDensity.current
  return remember(width, height, density) {
    with(density) {
      ReaderViewport(
        widthPx = width.roundToPx(),
        heightPx = height.roundToPx(),
        density = density.density,
      )
    }
  }
}

@Composable
private fun LaunchedReaderLayout(
  document: ReaderDocument,
  preferences: ReaderPreferences,
  viewport: ReaderViewport,
  typeface: Typeface,
  styleResolver: BlockStyleResolver,
  onPages: (List<ReaderPage>?) -> Unit,
  onPageCount: (Int) -> Unit,
  onLayoutFailed: () -> Unit,
) {
  var layoutGeneration by remember { mutableIntStateOf(0) }

  LaunchedEffect(document, preferences, viewport, typeface, styleResolver) {
    if (viewport.widthPx <= 0 || viewport.heightPx <= 0) {
      Log.d(
        TAG,
        "layout skipped: viewport ${viewport.widthPx}x${viewport.heightPx}px blocks=${document.blocks.size}",
      )
      return@LaunchedEffect
    }

    layoutGeneration++
    val generation = layoutGeneration
    Log.d(
      TAG,
      "layout start gen=$generation viewport=${viewport.widthPx}x${viewport.heightPx}px blocks=${document.blocks.size}",
    )

    val laidOut =
      layoutPages(
        document = document,
        viewport = viewport,
        styleResolver = styleResolver,
        generation = generation,
        currentGeneration = { layoutGeneration },
        onLayoutFailed = {
          onPages(null)
          onLayoutFailed()
        },
      ) ?: return@LaunchedEffect

    if (generation != layoutGeneration) {
      Log.d(TAG, "layout stale gen=$generation current=$layoutGeneration")
      return@LaunchedEffect
    }

    if (laidOut.isEmpty() || laidOut.all { page -> page.entries.isEmpty() }) {
      Log.d(TAG, "layout empty gen=$generation pages=${laidOut.size}")
      onPages(null)
      onLayoutFailed()
      return@LaunchedEffect
    }

    val firstPageEntries = laidOut.firstOrNull()?.entries?.size ?: 0
    Log.d(
      TAG,
      "layout done gen=$generation pages=${laidOut.size} firstPageEntries=$firstPageEntries",
    )
    onPages(laidOut)
    onPageCount(laidOut.size)
  }
}

private suspend fun layoutPages(
  document: ReaderDocument,
  viewport: ReaderViewport,
  styleResolver: BlockStyleResolver,
  generation: Int,
  currentGeneration: () -> Int,
  onLayoutFailed: () -> Unit,
): List<ReaderPage>? =
  try {
    withContext(Dispatchers.Default) {
      ReaderLayoutEngine()
        .layout(
          document = document,
          viewport = viewport,
          horizontalMarginPx = styleResolver.horizontalMarginPx(),
          verticalMarginPx = styleResolver.verticalMarginPx(),
          justify = styleResolver.justifyText(),
          styleResolver = styleResolver::resolve,
          shouldContinue = { generation == currentGeneration() },
        )
    }
  } catch (e: Exception) {
    if (generation == currentGeneration()) {
      Log.d(TAG, "layout failed gen=$generation", e)
      onLayoutFailed()
    }
    null
  }

@Composable
private fun ReaderPageAndroidView(
  page: ReaderPage,
  currentPage: Int,
  styleResolver: BlockStyleResolver,
  modifier: Modifier = Modifier,
) {
  // StaticLayout.draw() targets android.graphics.Canvas; a View gives reliable invalidation on
  // e-ink without pulling the full page through Compose recomposition every frame.
  AndroidView(
    modifier = modifier,
    factory = { ReaderPageCanvasView(it) },
    update = { view ->
      Log.d(TAG, "update page=$currentPage entries=${page.entries.size}")
      view.setPage(
        page = page,
        horizontalMarginPx = styleResolver.horizontalMarginPx(),
        verticalMarginPx = styleResolver.verticalMarginPx(),
      )
    },
  )
}

private fun loadReaderTypeface(
  assets: android.content.res.AssetManager,
  font: ReaderFont,
): Typeface =
  runCatching { Typeface.createFromAsset(assets, font.regularAssetPath) }
    .getOrElse { Typeface.SERIF }
