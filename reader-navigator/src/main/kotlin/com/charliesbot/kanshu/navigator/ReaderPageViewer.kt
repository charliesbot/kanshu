package com.charliesbot.kanshu.navigator

import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
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
import com.charliesbot.kanshu.navigator.render.ReaderPageTapZone
import com.charliesbot.kanshu.navigator.render.SelectionPageTurnDirection
import com.charliesbot.kanshu.navigator.selection.SelectionCarryState
import com.charliesbot.kanshu.navigator.selection.TextSelection
import com.charliesbot.kanshu.navigator.selection.toSelectionTextPrefix
import com.charliesbot.kanshu.navigator.selection.toSelectionTextSuffix
import com.charliesbot.kanshu.navigator.selection.turnSelectionPage
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ReaderPageViewer"

@Composable
fun ReaderPageViewer(
  document: ReaderDocument,
  preferences: ReaderPreferences,
  currentPage: Int,
  onPageCount: (Int) -> Unit,
  onLayoutDiagnostics: (ReaderLayoutDiagnostics) -> Unit = {},
  onLayoutFailed: () -> Unit = {},
  onPreviousPage: (() -> Unit)? = null,
  onCenterTap: (() -> Unit)? = null,
  onNextPage: (() -> Unit)? = null,
  onTextSelected: ((String, RectF) -> Unit)? = null,
  onSelectionCleared: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val typeface = remember(preferences.font) { loadReaderTypeface(context.assets, preferences.font) }
  val selectionLocale = remember(document.language) { document.language.toSelectionLocale() }

  BoxWithConstraints(modifier = modifier) {
    val viewport = rememberReaderViewport(maxWidth, maxHeight)
    var pages by remember(document) { mutableStateOf<List<ReaderPage>?>(null) }
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
      onLayoutDiagnostics = onLayoutDiagnostics,
      onLayoutFailed = onLayoutFailed,
    )

    val page = pages?.getOrNull(currentPage.coerceAtLeast(0))
    var selectionCarryState by remember(document) { mutableStateOf(SelectionCarryState()) }
    var pendingSelectionSeedPage by remember(document) { mutableStateOf<Int?>(null) }
    var pendingSelectionSeedAtPageEnd by remember(document) { mutableStateOf(false) }
    var pendingRestoredSelection by remember(document) { mutableStateOf<TextSelection?>(null) }
    if (page != null) {
      val shouldSeedSelection = pendingSelectionSeedPage == currentPage
      ReaderPageAndroidView(
        page = page,
        currentPage = currentPage,
        selectionTextPrefix = selectionCarryState.prefixPages.toSelectionTextPrefix(),
        selectionTextSuffix = selectionCarryState.suffixPages.toSelectionTextSuffix(),
        restoredSelection = if (shouldSeedSelection) pendingRestoredSelection else null,
        seedSelectionAtPageStart =
          shouldSeedSelection && pendingRestoredSelection == null && !pendingSelectionSeedAtPageEnd,
        seedSelectionAtPageEnd =
          shouldSeedSelection && pendingRestoredSelection == null && pendingSelectionSeedAtPageEnd,
        selectionLocale = selectionLocale,
        styleResolver = styleResolver,
        onTapZone = { zone ->
          when (zone) {
            ReaderPageTapZone.Previous -> onPreviousPage?.invoke()
            ReaderPageTapZone.Center -> onCenterTap?.invoke()
            ReaderPageTapZone.Next -> onNextPage?.invoke()
          }
        },
        onTextSelected = { text, anchor -> onTextSelected?.invoke(text, anchor) },
        onSelectionCleared = {
          selectionCarryState = SelectionCarryState()
          pendingSelectionSeedPage = null
          pendingSelectionSeedAtPageEnd = false
          pendingRestoredSelection = null
          onSelectionCleared?.invoke()
        },
        onSelectionPageTurn = { direction, _, pageSelectedText, currentSelection ->
          val laidOutPages = pages ?: return@ReaderPageAndroidView false
          // Cross-spine selection needs ownership above this composable because ReaderScreen keys
          // ReaderPageViewer by spine index. Keep this slice within the current laid-out document.
          val turn =
            selectionCarryState.turnSelectionPage(
              direction = direction,
              currentPage = currentPage,
              lastPageIndex = laidOutPages.lastIndex,
              pageSelectedText = pageSelectedText,
              currentSelection = currentSelection,
            ) ?: return@ReaderPageAndroidView false
          selectionCarryState = turn.carryState
          pendingSelectionSeedPage = turn.targetPage
          pendingSelectionSeedAtPageEnd = turn.seedAtPageEnd
          pendingRestoredSelection = turn.restoredSelection
          when (direction) {
            SelectionPageTurnDirection.Previous -> onPreviousPage?.invoke()
            SelectionPageTurnDirection.Next -> onNextPage?.invoke()
          }
          true
        },
        onSelectionSeeded = {
          if (pendingSelectionSeedPage == currentPage) {
            pendingSelectionSeedPage = null
            pendingSelectionSeedAtPageEnd = false
            pendingRestoredSelection = null
          }
        },
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

internal fun String?.toSelectionLocale(): Locale =
  if (isNullOrBlank()) {
    Locale.getDefault()
  } else {
    Locale.forLanguageTag(this).takeUnless { it.language.isBlank() } ?: Locale.getDefault()
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
  onLayoutDiagnostics: (ReaderLayoutDiagnostics) -> Unit,
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

    val layoutResult =
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
    val laidOut = layoutResult.pages

    if (generation != layoutGeneration) {
      Log.d(TAG, "layout stale gen=$generation current=$layoutGeneration")
      return@LaunchedEffect
    }

    if (!laidOut.hasRenderablePage()) {
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
    onLayoutDiagnostics(
      ReaderLayoutDiagnostics(
        blockCount = document.blocks.size,
        pageCount = laidOut.size,
        paginationMillis = layoutResult.paginationMillis,
      )
    )
  }
}

private suspend fun layoutPages(
  document: ReaderDocument,
  viewport: ReaderViewport,
  styleResolver: BlockStyleResolver,
  generation: Int,
  currentGeneration: () -> Int,
  onLayoutFailed: () -> Unit,
): LayoutResult? =
  try {
    val startedAt = SystemClock.elapsedRealtime()
    val pages =
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
    LayoutResult(pages = pages, paginationMillis = SystemClock.elapsedRealtime() - startedAt)
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    if (generation == currentGeneration()) {
      Log.d(TAG, "layout failed gen=$generation", e)
      onLayoutFailed()
    }
    null
  }

private data class LayoutResult(val pages: List<ReaderPage>, val paginationMillis: Long)

internal fun List<ReaderPage>.hasRenderablePage(): Boolean = isNotEmpty()

@Composable
private fun ReaderPageAndroidView(
  page: ReaderPage,
  currentPage: Int,
  selectionTextPrefix: String,
  selectionTextSuffix: String,
  restoredSelection: TextSelection?,
  seedSelectionAtPageStart: Boolean,
  seedSelectionAtPageEnd: Boolean,
  selectionLocale: Locale,
  styleResolver: BlockStyleResolver,
  onTapZone: (ReaderPageTapZone) -> Unit,
  onTextSelected: (String, RectF) -> Unit,
  onSelectionCleared: () -> Unit,
  onSelectionPageTurn: (SelectionPageTurnDirection, String, String, TextSelection) -> Boolean,
  onSelectionSeeded: () -> Unit,
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
        onTapZone = onTapZone,
        onTextSelected = onTextSelected,
        onSelectionCleared = onSelectionCleared,
        onSelectionPageTurn = onSelectionPageTurn,
        selectionTextPrefix = selectionTextPrefix,
        selectionTextSuffix = selectionTextSuffix,
        selectionLocale = selectionLocale,
        restoredSelection = restoredSelection,
        seedSelectionAtPageStart = seedSelectionAtPageStart,
        seedSelectionAtPageEnd = seedSelectionAtPageEnd,
      )
      if (restoredSelection != null || seedSelectionAtPageStart || seedSelectionAtPageEnd) {
        onSelectionSeeded()
      }
    },
    onRelease = { view -> view.release() },
  )
}

private fun loadReaderTypeface(
  assets: android.content.res.AssetManager,
  font: ReaderFont,
): Typeface =
  runCatching { Typeface.createFromAsset(assets, font.regularAssetPath) }
    .getOrElse { Typeface.SERIF }
