package com.charliesbot.kanshu.core.ui.components

import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.composeunstyled.Sheet
import com.composeunstyled.SheetDetent
import com.composeunstyled.UnstyledBottomSheet
import com.composeunstyled.rememberBottomSheetState

// Non-modal sheet anchored to the bottom of the parent Box. The non-modal variant is chosen so
// the sheet renders in the same window as the reader and FullScreenMode keeps the status/nav
// bars hidden. The mounted-only-when-open shape, FullyExpanded initial detent, and snap()
// animation spec together ensure every transition resolves in a single composition — required
// on e-ink where every recomposition is a visible refresh.
//
// A transparent tap blocker stands in for the scrim a modal sheet would provide. It absorbs
// taps on the page behind the sheet (so a stray tap doesn't turn a page) and is the only way
// to dismiss the sheet — there's no drag handle and only one detent, since smooth drag
// animations don't render well on e-ink anyway.
@Composable
fun KanshuBottomSheet(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (!isOpen) return

  Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })

  val state =
    rememberBottomSheetState(
      initialDetent = SheetDetent.FullyExpanded,
      detents = listOf(SheetDetent.FullyExpanded),
      animationSpec = snap(),
    )

  UnstyledBottomSheet(state = state, modifier = Modifier.fillMaxSize()) {
    Sheet(
      modifier =
        modifier
          .fillMaxWidth()
          .background(KanshuTheme.colors.background)
          .border(2.dp, KanshuTheme.colors.border)
    ) {
      Column(Modifier.fillMaxWidth(), content = content)
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun KanshuBottomSheetPreview() {
  KanshuTheme {
    Box(Modifier.fillMaxWidth().height(400.dp)) {
      KanshuBottomSheet(isOpen = true, onDismiss = {}) {
        Box(Modifier.fillMaxWidth().height(240.dp))
      }
    }
  }
}
