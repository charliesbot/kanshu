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
  val density = LocalDensity.current
  val typeface = remember(preferences.font) { loadReaderTypeface(context.assets, preferences.font) }

  BoxWithConstraints(modifier = modifier) {
    val viewport =
      remember(maxWidth, maxHeight, density) {
        with(density) {
          ReaderViewport(
            widthPx = maxWidth.roundToPx(),
            heightPx = maxHeight.roundToPx(),
            density = density.density,
          )
        }
      }

    var pages by remember { mutableStateOf<List<ReaderPage>?>(null) }
    var layoutGeneration by remember { mutableIntStateOf(0) }
    val styleResolver =
      remember(preferences, viewport.density, typeface) {
        BlockStyleResolver(preferences, typeface, viewport.density)
      }

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
                shouldContinue = { generation == layoutGeneration },
              )
          }
        } catch (e: Exception) {
          if (generation == layoutGeneration) {
            Log.d(TAG, "layout failed gen=$generation", e)
            pages = null
            onLayoutFailed()
          }
          return@LaunchedEffect
        }

      if (generation != layoutGeneration) {
        Log.d(TAG, "layout stale gen=$generation current=$layoutGeneration")
        return@LaunchedEffect
      }

      if (laidOut.isEmpty() || laidOut.all { page -> page.entries.isEmpty() }) {
        Log.d(TAG, "layout empty gen=$generation pages=${laidOut.size}")
        pages = null
        onLayoutFailed()
        return@LaunchedEffect
      }

      val firstPageEntries = laidOut.firstOrNull()?.entries?.size ?: 0
      Log.d(
        TAG,
        "layout done gen=$generation pages=${laidOut.size} firstPageEntries=$firstPageEntries",
      )
      pages = laidOut
      onPageCount(laidOut.size)
    }

    val page = pages?.getOrNull(currentPage.coerceAtLeast(0))

    if (page != null) {
      // StaticLayout.draw() targets android.graphics.Canvas; a View gives reliable invalidation on
      // e-ink without pulling the full page through Compose recomposition every frame.
      AndroidView(
        modifier = Modifier.fillMaxSize(),
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
  }
}

private fun loadReaderTypeface(
  assets: android.content.res.AssetManager,
  font: ReaderFont,
): Typeface =
  runCatching { Typeface.createFromAsset(assets, font.regularAssetPath) }
    .getOrElse { Typeface.SERIF }
