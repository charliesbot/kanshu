package com.charliesbot.kanshu.features.reader

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charliesbot.kanshu.core.sync.RemoteProgress
import com.charliesbot.kanshu.core.ui.components.KanshuBottomSheet
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.system.FullScreenMode
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

// Reading mode defaults to zero persistent app UI per the PRD: system bars are hidden via
// FullScreenMode for the lifetime of this screen, and pagination is driven by swipe gestures
// and Readium's tap-edge adapter. A center tap reveals the ReaderOverlay (top chrome + bottom
// chrome), which dismisses on tap of the middle zone. Pagination, chapter advancement, and
// rendering are owned by EpubNavigatorFragment via Readium.
@Composable
fun ReaderScreen(
  seriesId: Int,
  title: String,
  viewModel: ReaderViewModel = koinViewModel { parametersOf(seriesId) },
) {
  var overlayVisible by remember { mutableStateOf(false) }
  FullScreenMode(enabled = !overlayVisible)
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val chapterState by viewModel.chapterState.collectAsStateWithLifecycle()
  val remoteSuggestion by viewModel.remoteSuggestion.collectAsStateWithLifecycle()
  ReaderContent(
    uiState = uiState,
    fallbackTitle = title,
    overlayVisible = overlayVisible,
    onOverlayVisibleChange = { overlayVisible = it },
    chapterState = chapterState,
    onLocatorChanged = viewModel::onLocatorChanged,
    navigateTo = viewModel.navigateTo,
    alreadyAtFurthest = viewModel.alreadyAtFurthest,
    remoteSuggestion = remoteSuggestion,
    onAcceptSuggestion = viewModel::acceptRemoteSuggestion,
    onDismissSuggestion = viewModel::dismissRemoteSuggestion,
    onSyncToFurthest = viewModel::syncToFurthestPageRead,
  )
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun ReaderContent(
  uiState: ReaderUiState,
  fallbackTitle: String,
  overlayVisible: Boolean,
  onOverlayVisibleChange: (Boolean) -> Unit,
  chapterState: ChapterState,
  onLocatorChanged: (Locator) -> Unit,
  navigateTo: kotlinx.coroutines.flow.SharedFlow<Locator>,
  alreadyAtFurthest: kotlinx.coroutines.flow.SharedFlow<Unit>,
  remoteSuggestion: RemoteProgress?,
  onAcceptSuggestion: () -> Unit,
  onDismissSuggestion: () -> Unit,
  onSyncToFurthest: () -> Unit,
) {
  KanshuScaffold {
    when (uiState) {
      ReaderUiState.Loading ->
        StatusText(text = fallbackTitle.ifBlank { stringResource(R.string.reader_status_loading) })
      is ReaderUiState.Ready ->
        ReaderBody(
          uiState = uiState,
          fallbackTitle = fallbackTitle,
          overlayVisible = overlayVisible,
          onOverlayVisibleChange = onOverlayVisibleChange,
          chapterState = chapterState,
          onLocatorChanged = onLocatorChanged,
          navigateTo = navigateTo,
          alreadyAtFurthest = alreadyAtFurthest,
          remoteSuggestion = remoteSuggestion,
          onAcceptSuggestion = onAcceptSuggestion,
          onDismissSuggestion = onDismissSuggestion,
          onSyncToFurthest = onSyncToFurthest,
        )
      ReaderUiState.Error.NotFound ->
        StatusText(text = stringResource(R.string.reader_error_not_found))
      ReaderUiState.Error.ParseFailed ->
        StatusText(text = stringResource(R.string.reader_error_parse_failed))
      ReaderUiState.Error.ReadFailed ->
        StatusText(text = stringResource(R.string.reader_error_read_failed))
    }
  }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun ReaderBody(
  uiState: ReaderUiState.Ready,
  fallbackTitle: String,
  overlayVisible: Boolean,
  onOverlayVisibleChange: (Boolean) -> Unit,
  chapterState: ChapterState,
  onLocatorChanged: (Locator) -> Unit,
  navigateTo: kotlinx.coroutines.flow.SharedFlow<Locator>,
  alreadyAtFurthest: kotlinx.coroutines.flow.SharedFlow<Unit>,
  remoteSuggestion: RemoteProgress?,
  onAcceptSuggestion: () -> Unit,
  onDismissSuggestion: () -> Unit,
  onSyncToFurthest: () -> Unit,
) {
  var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
  var readerPrefsOpen by remember { mutableStateOf(false) }
  val openReaderPrefs = {
    onOverlayVisibleChange(false)
    readerPrefsOpen = true
  }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val toastText = stringResource(R.string.reader_sync_already_at_furthest)
  LaunchedEffect(navigator) { navigator?.currentLocator?.collect(onLocatorChanged) }
  LaunchedEffect(navigator) { navigateTo.collect { target -> navigator?.go(target) } }
  LaunchedEffect(Unit) {
    alreadyAtFurthest.collect { Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show() }
  }
  Box(Modifier.fillMaxSize()) {
    EpubNavigatorHost(
      factory = uiState.factory,
      onNavigatorReady = { navigator = it },
      onCenterTap = { onOverlayVisibleChange(true) },
      initialLocator = uiState.initialLocator,
      modifier =
        Modifier.fillMaxSize()
          .horizontalSwipeToPageTurn(
            enabled = navigator != null,
            onSwipeForward = { scope.launch { navigator?.goForward() } },
            onSwipeBackward = { scope.launch { navigator?.goBackward() } },
          ),
    )
    if (overlayVisible) {
      ReaderOverlay(
        title = uiState.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
        chapterTitle = chapterState.title,
        prevChapterEnabled = chapterState.prevLocator != null,
        nextChapterEnabled = chapterState.nextLocator != null,
        onPrevChapter = {
          chapterState.prevLocator?.let { target -> scope.launch { navigator?.go(target) } }
        },
        onNextChapter = {
          chapterState.nextLocator?.let { target -> scope.launch { navigator?.go(target) } }
        },
        onSyncToFurthest = onSyncToFurthest,
        onOpenReaderPrefs = openReaderPrefs,
        onDismiss = { onOverlayVisibleChange(false) },
      )
    }
    KanshuBottomSheet(isOpen = readerPrefsOpen, onDismiss = { readerPrefsOpen = false }) {
      KanshuText(text = "hi", modifier = Modifier.padding(24.dp))
    }
    if (remoteSuggestion != null) {
      RemoteProgressPrompt(
        suggestion = remoteSuggestion,
        onApply = onAcceptSuggestion,
        onDismiss = onDismissSuggestion,
      )
    }
  }
}

// Intercepts horizontal drags BEFORE Readium's R2ViewPager sees them (PointerEventPass.Initial)
// so the user-driven swipe never triggers ViewPager's smooth-scroll animation. Once a gesture
// is locked as horizontal we consume every subsequent change for that pointer; on finger up we
// trigger an instant page turn via the navigator's goForward/goBackward (which already default
// to animated=false). Vertical drags and short taps are not consumed and pass through, so
// Readium's tap-edge navigation, text selection, and scroll inside the page still work.
//
// Single-pointer scope: we lock onto the first finger down and ignore subsequent pointers (no
// pinch-to-zoom etc.). Acceptable for V1 — selection / multi-touch may want a dedicated path
// later. Cancellation is handled by changedToUp(); if the pointer disappears entirely without
// a release event we exit silently rather than firing.
@Composable
private fun Modifier.horizontalSwipeToPageTurn(
  enabled: Boolean,
  onSwipeForward: () -> Unit,
  onSwipeBackward: () -> Unit,
): Modifier {
  if (!enabled) return this
  val viewConfiguration = LocalViewConfiguration.current
  val forward by rememberUpdatedState(onSwipeForward)
  val backward by rememberUpdatedState(onSwipeBackward)
  return this.pointerInput(Unit) {
    val touchSlop = viewConfiguration.touchSlop
    val swipeThreshold = touchSlop * 4f
    while (true) {
      awaitPointerEventScope {
        val down =
          awaitPointerEvent(pass = PointerEventPass.Initial).changes.firstOrNull { it.pressed }
            ?: return@awaitPointerEventScope
        var totalDx = 0f
        var totalDy = 0f
        var locked = false
        var horizontal = false
        while (true) {
          val event = awaitPointerEvent(pass = PointerEventPass.Initial)
          val change =
            event.changes.firstOrNull { it.id == down.id } ?: return@awaitPointerEventScope
          if (change.changedToUp()) {
            if (horizontal && abs(totalDx) >= swipeThreshold) {
              if (totalDx < 0) forward() else backward()
            }
            return@awaitPointerEventScope
          }
          val delta = change.positionChange()
          totalDx += delta.x
          totalDy += delta.y
          if (!locked && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
            horizontal = abs(totalDx) > abs(totalDy)
            locked = true
          }
          if (locked && horizontal) change.consume()
        }
      }
    }
  }
}

@Composable
private fun StatusText(text: String) {
  KanshuText(
    text = text,
    style = KanshuTheme.typography.bodyLarge,
    modifier =
      Modifier.fillMaxSize().padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
  )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReaderScreenLoadingPreview() {
  KanshuTheme {
    ReaderContent(
      uiState = ReaderUiState.Loading,
      fallbackTitle = "Alice's Adventures in Wonderland",
      overlayVisible = false,
      onOverlayVisibleChange = {},
      chapterState = ChapterState.Empty,
      onLocatorChanged = {},
      navigateTo = kotlinx.coroutines.flow.MutableSharedFlow(),
      alreadyAtFurthest = kotlinx.coroutines.flow.MutableSharedFlow(),
      remoteSuggestion = null,
      onAcceptSuggestion = {},
      onDismissSuggestion = {},
      onSyncToFurthest = {},
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReaderScreenErrorPreview() {
  KanshuTheme {
    ReaderContent(
      uiState = ReaderUiState.Error.ParseFailed,
      fallbackTitle = "",
      overlayVisible = false,
      onOverlayVisibleChange = {},
      chapterState = ChapterState.Empty,
      onLocatorChanged = {},
      navigateTo = kotlinx.coroutines.flow.MutableSharedFlow(),
      alreadyAtFurthest = kotlinx.coroutines.flow.MutableSharedFlow(),
      remoteSuggestion = null,
      onAcceptSuggestion = {},
      onDismissSuggestion = {},
      onSyncToFurthest = {},
    )
  }
}
