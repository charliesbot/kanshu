package com.charliesbot.kanshu.navigator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.charliesbot.kanshu.navigator.engine.ImageBounds
import com.charliesbot.kanshu.navigator.engine.PageEntry
import com.charliesbot.kanshu.navigator.engine.ReaderLayoutEngine
import com.charliesbot.kanshu.navigator.engine.ReaderPage
import com.charliesbot.kanshu.navigator.engine.ReaderViewport
import com.charliesbot.kanshu.navigator.model.ImageBlock
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.render.ReaderImageDecoder
import com.charliesbot.kanshu.navigator.render.ReaderPageCanvasView
import com.charliesbot.kanshu.navigator.render.ReaderPageTapZone
import com.charliesbot.kanshu.navigator.render.SelectionPageTurnDirection
import com.charliesbot.kanshu.navigator.selection.SelectionCarryState
import com.charliesbot.kanshu.navigator.selection.TextSelection
import com.charliesbot.kanshu.navigator.selection.toSelectionTextPrefix
import com.charliesbot.kanshu.navigator.selection.toSelectionTextSuffix
import com.charliesbot.kanshu.navigator.selection.turnSelectionPage
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ReaderPageViewer"

@Composable
fun ReaderPageViewer(
  document: ReaderDocument,
  preferences: ReaderPreferences,
  currentPage: Int,
  onPageCount: (Int) -> Unit,
  resourceLoader: ReaderResourceLoader? = null,
  onLayoutDiagnostics: (ReaderLayoutDiagnostics) -> Unit = {},
  onLayoutFailed: () -> Unit = {},
  onPreviousPage: (() -> Unit)? = null,
  onCenterTap: (() -> Unit)? = null,
  onNextPage: (() -> Unit)? = null,
  onTextSelected: ((String, RectF) -> Unit)? = null,
  onSelectionCleared: (() -> Unit)? = null,
  onLinkTapped: ((String) -> Unit)? = null,
  imageCache: ReaderImageCache = rememberReaderImageCache(),
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
      resourceLoader = resourceLoader,
      imageBoundsCache = imageCache.bounds,
      onPages = { laidOut -> pages = laidOut },
      onPageCount = onPageCount,
      onLayoutDiagnostics = onLayoutDiagnostics,
      onLayoutFailed = onLayoutFailed,
    )

    // A page is presented only once its images are decoded, failed, or timed out — images appear
    // with the page (the decode hides inside the e-ink refresh) instead of repainting after it.
    var presentedPageIndex by remember(document) { mutableStateOf<Int?>(null) }
    val laidOut = pages
    LaunchedEffect(laidOut, currentPage, resourceLoader, imageCache) {
      val pagesNow = laidOut ?: return@LaunchedEffect
      if (pagesNow.isEmpty()) return@LaunchedEffect
      val targetIndex = currentPage.coerceIn(0, pagesNow.lastIndex)
      val loader = resourceLoader
      if (loader == null) {
        presentedPageIndex = targetIndex
        return@LaunchedEffect
      }
      val pending = pagesNow[targetIndex].pendingImageEntries(imageCache.bitmaps.keys)
      if (pending.isNotEmpty()) {
        withTimeoutOrNull(PRESENTATION_DECODE_TIMEOUT_MS) {
          decodeImagesInto(imageCache, loader, pending)
        }
      }
      presentedPageIndex = targetIndex
      // Finish anything the timeout cut off, then decode one page ahead and behind so the
      // next turn's gate finds its bitmaps already cached.
      val followUp =
        listOfNotNull(
            pagesNow.getOrNull(targetIndex),
            pagesNow.getOrNull(targetIndex - 1),
            pagesNow.getOrNull(targetIndex + 1),
          )
          .flatMap { it.pendingImageEntries(imageCache.bitmaps.keys) }
      decodeImagesInto(imageCache, loader, followUp)
    }

    val presentedIndex = presentedPageIndex
    val page = presentedIndex?.let { laidOut?.getOrNull(it) }
    var selectionCarryState by remember(document) { mutableStateOf(SelectionCarryState()) }
    var pendingSelectionSeedPage by remember(document) { mutableStateOf<Int?>(null) }
    var pendingSelectionSeedAtPageEnd by remember(document) { mutableStateOf(false) }
    var pendingRestoredSelection by remember(document) { mutableStateOf<TextSelection?>(null) }
    if (page != null && presentedIndex != null) {
      val shouldSeedSelection = pendingSelectionSeedPage == presentedIndex
      ReaderPageAndroidView(
        page = page,
        currentPage = presentedIndex,
        selectionTextPrefix = selectionCarryState.prefixPages.toSelectionTextPrefix(),
        selectionTextSuffix = selectionCarryState.suffixPages.toSelectionTextSuffix(),
        restoredSelection = if (shouldSeedSelection) pendingRestoredSelection else null,
        seedSelectionAtPageStart =
          shouldSeedSelection && pendingRestoredSelection == null && !pendingSelectionSeedAtPageEnd,
        seedSelectionAtPageEnd =
          shouldSeedSelection && pendingRestoredSelection == null && pendingSelectionSeedAtPageEnd,
        selectionLocale = selectionLocale,
        styleResolver = styleResolver,
        imageBitmaps =
          imageCache.bitmaps.entries
            .mapNotNull { (href, bitmap) -> bitmap?.let { href to it } }
            .toMap(),
        onTapZone = { zone ->
          when (zone) {
            ReaderPageTapZone.Previous -> onPreviousPage?.invoke()
            ReaderPageTapZone.Center -> onCenterTap?.invoke()
            ReaderPageTapZone.Next -> onNextPage?.invoke()
          }
        },
        onLinkTapped = onLinkTapped,
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
              currentPage = presentedIndex,
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
          if (pendingSelectionSeedPage == presentedIndex) {
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
  resourceLoader: ReaderResourceLoader?,
  imageBoundsCache: MutableMap<String, ImageBounds>,
  onPages: (List<ReaderPage>?) -> Unit,
  onPageCount: (Int) -> Unit,
  onLayoutDiagnostics: (ReaderLayoutDiagnostics) -> Unit,
  onLayoutFailed: () -> Unit,
) {
  var layoutGeneration by remember { mutableIntStateOf(0) }

  LaunchedEffect(document, preferences, viewport, typeface, styleResolver, resourceLoader) {
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

    val imageBounds = resolveImageBounds(document, resourceLoader, imageBoundsCache)

    val layoutResult =
      layoutPages(
        document = document,
        viewport = viewport,
        styleResolver = styleResolver,
        imageBounds = imageBounds,
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
  imageBounds: Map<String, ImageBounds>,
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
            imageBounds = imageBounds::get,
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

/**
 * Header-only decode of every distinct image resource in the document. Runs before pagination so
 * the engine can reserve true image heights; failures fall back to the placeholder height.
 */
private suspend fun resolveImageBounds(
  document: ReaderDocument,
  loader: ReaderResourceLoader?,
  cache: MutableMap<String, ImageBounds>,
): Map<String, ImageBounds> {
  if (loader == null) return emptyMap()
  val hrefs =
    document.blocks
      .filterIsInstance<ImageBlock>()
      .map { it.resourceHref }
      .filter { it.isNotBlank() }
      .distinct()
  if (hrefs.isEmpty()) return emptyMap()
  val missing = hrefs.filterNot(cache::containsKey)
  if (missing.isNotEmpty()) {
    val fetched =
      withContext(Dispatchers.IO) {
        buildMap {
          missing.forEach { href ->
            val bytes = loader.readOrNull(href) ?: return@forEach
            decodeImageBounds(bytes)?.let { bounds -> put(href, bounds) }
          }
        }
      }
    // Merged on the caller's context so a cancelled layout coroutine never mutates the cache.
    cache.putAll(fetched)
  }
  return hrefs.mapNotNull { href -> cache[href]?.let { bounds -> href to bounds } }.toMap()
}

// Bounds how long the gate holds a page turn for image decoding. Soft cap: an in-flight
// read/decode is not interruptible, so a pathologically slow image can overshoot; on timeout the
// page presents with the placeholder and the decode finishes in the follow-up pass.
private const val PRESENTATION_DECODE_TIMEOUT_MS = 250L

/** Decodes entries one at a time, merging each result on the caller's context. */
private suspend fun decodeImagesInto(
  cache: ReaderImageCache,
  loader: ReaderResourceLoader,
  entries: List<PageEntry.Image>,
) {
  entries.forEach { entry ->
    if (cache.bitmaps.containsKey(entry.resourceHref)) return@forEach
    val bitmap =
      withContext(Dispatchers.IO) {
        loader.readOrNull(entry.resourceHref)?.let {
          ReaderImageDecoder.decode(it, entry.widthPx.roundToInt())
        }
      }
    cache.bitmaps[entry.resourceHref] = bitmap
  }
}

private suspend fun ReaderResourceLoader.readOrNull(href: String): ByteArray? =
  try {
    read(href)
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Log.d(TAG, "resource read failed href=$href", e)
    null
  }

private fun decodeImageBounds(bytes: ByteArray): ImageBounds? {
  val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
  return if (options.outWidth > 0 && options.outHeight > 0) {
    ImageBounds(intrinsicWidthPx = options.outWidth, intrinsicHeightPx = options.outHeight)
  } else {
    null
  }
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
  imageBitmaps: Map<String, Bitmap>,
  onTapZone: (ReaderPageTapZone) -> Unit,
  onLinkTapped: ((String) -> Unit)?,
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
        imageBitmaps = imageBitmaps,
        onTapZone = onTapZone,
        onLinkTapped = onLinkTapped,
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
