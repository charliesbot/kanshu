package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charliesbot.kanshu.core.ui.components.KanshuButton
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi

// Reading mode defaults to zero persistent app UI per the PRD. The Prev/Next row is the V1
// navigation surface; tap zones and the reader overlay come in a follow-up PR. Pagination,
// chapter advancement, and rendering are owned by EpubNavigatorFragment via Readium.
@Composable
fun ReaderScreen(
  seriesId: Int,
  title: String,
  viewModel: ReaderViewModel = koinViewModel { parametersOf(seriesId) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  ReaderContent(uiState = uiState, fallbackTitle = title)
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun ReaderContent(uiState: ReaderUiState, fallbackTitle: String) {
  KanshuScaffold {
    when (uiState) {
      ReaderUiState.Loading ->
        StatusText(text = fallbackTitle.ifBlank { stringResource(R.string.reader_status_loading) })
      is ReaderUiState.Ready -> ReaderBody(uiState = uiState)
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
private fun ReaderBody(uiState: ReaderUiState.Ready) {
  var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
  val scope = rememberCoroutineScope()
  Column(modifier = Modifier.fillMaxSize()) {
    EpubNavigatorHost(
      factory = uiState.factory,
      onNavigatorReady = { navigator = it },
      modifier =
        Modifier.weight(1f)
          .fillMaxWidth()
          .horizontalSwipeToPageTurn(
            enabled = navigator != null,
            onSwipeForward = { scope.launch { navigator?.goForward() } },
            onSwipeBackward = { scope.launch { navigator?.goBackward() } },
          ),
    )
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      KanshuButton(
        text = stringResource(R.string.reader_prev),
        onClick = { scope.launch { navigator?.goBackward() } },
        enabled = navigator != null,
      )
      KanshuButton(
        text = stringResource(R.string.reader_next),
        onClick = { scope.launch { navigator?.goForward() } },
        enabled = navigator != null,
      )
    }
  }
}

// Intercepts horizontal drags BEFORE Readium's R2ViewPager sees them (PointerEventPass.Initial)
// so the user-driven swipe never triggers ViewPager's smooth-scroll animation. Once a gesture
// is locked as horizontal we consume every subsequent change for that pointer; on finger up we
// trigger an instant page turn via the navigator's goForward/goBackward (which already default
// to animated=false). Vertical drags and short taps are not consumed and pass through, so
// Readium's tap-to-paginate, text selection, and scroll inside the page still work.
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
          if (!change.pressed) {
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
  BasicText(
    text = text,
    style = KanshuTheme.typography.body.copy(color = KanshuTheme.colors.onBackground),
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
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReaderScreenErrorPreview() {
  KanshuTheme { ReaderContent(uiState = ReaderUiState.Error.ParseFailed, fallbackTitle = "") }
}
