package com.charliesbot.kanshu.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.composeunstyled.DragIndication
import com.composeunstyled.Scrim
import com.composeunstyled.Sheet
import com.composeunstyled.SheetDetent
import com.composeunstyled.UnstyledModalBottomSheet
import com.composeunstyled.rememberModalBottomSheetState

// Modal sheet anchored to the bottom of the reader. `jumpTo` is used everywhere instead of
// `animateTo`/`targetDetent` — sliding animations ghost on e-ink, so the sheet must cut in/out.
// `DragIndication` keeps drag-to-dismiss working; the LaunchedEffect on `currentDetent` mirrors
// that user-initiated dismiss back to the parent's `isOpen` flag.
@Composable
fun KanshuBottomSheet(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  val state =
    rememberModalBottomSheetState(
      initialDetent = SheetDetent.Hidden,
      detents = listOf(SheetDetent.Hidden, SheetDetent.FullyExpanded),
    )

  LaunchedEffect(isOpen) {
    state.jumpTo(if (isOpen) SheetDetent.FullyExpanded else SheetDetent.Hidden)
  }

  LaunchedEffect(state.currentDetent) {
    if (isOpen && state.currentDetent == SheetDetent.Hidden) onDismiss()
  }

  UnstyledModalBottomSheet(
    state = state,
    // Transparent scrim instead of a dimmed one: a gray wash adds nothing on e-ink and
    // degrades the page contrast. The Scrim still intercepts taps for outside-to-dismiss.
    overlay = { Scrim(scrimColor = Color.Transparent) },
  ) {
    Sheet(
      modifier =
        modifier
          .fillMaxWidth()
          .background(KanshuTheme.colors.background)
          .border(2.dp, KanshuTheme.colors.border)
    ) {
      Column(Modifier.fillMaxWidth()) {
        Box(
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          contentAlignment = Alignment.Center,
        ) {
          DragIndication(
            modifier =
              Modifier.size(width = 40.dp, height = 4.dp).background(KanshuTheme.colors.border)
          )
        }
        content()
      }
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
