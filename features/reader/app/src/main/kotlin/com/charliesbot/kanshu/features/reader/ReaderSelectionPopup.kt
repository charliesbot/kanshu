package com.charliesbot.kanshu.features.reader

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.navigator.ReaderSelectionInfo
import com.charliesbot.kanshu.strings.R

/**
 * The actions offered over a live selection, anchored beside the selected text.
 *
 * Positioned by [readerSelectionPopupPosition], which keeps it inside the viewport and flips it
 * above the selection when there is no room below.
 */
@Composable
internal fun ReaderSelectionPopup(selection: ReaderSelectionInfo, onHighlight: () -> Unit) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds()) {
    val density = LocalDensity.current
    val marginPx = with(density) { 8.dp.roundToPx() }
    val maxPopupWidth = (maxWidth - 16.dp).coerceAtLeast(48.dp)
    var popupWidthPx by remember { mutableStateOf(0) }
    var popupHeightPx by remember { mutableStateOf(0) }

    Box(
      modifier =
        Modifier.offset {
            readerSelectionPopupPosition(
                anchor = selection.anchor,
                viewportWidthPx = constraints.maxWidth,
                viewportHeightPx = constraints.maxHeight,
                popupWidthPx = popupWidthPx,
                popupHeightPx = popupHeightPx,
                marginPx = marginPx,
              )
              .toIntOffset()
          }
          .widthIn(max = maxPopupWidth)
          .onSizeChanged { size ->
            popupWidthPx = size.width
            popupHeightPx = size.height
          }
          .background(KanshuTheme.colors.background)
          .border(1.dp, KanshuTheme.colors.border)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        KanshuText(
          text = selection.text,
          style = KanshuTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false).padding(horizontal = 10.dp, vertical = 6.dp),
        )
        // 48dp minimum per the e-ink touch-target rule: refresh latency makes users re-tap
        // anything smaller.
        Box(
          modifier =
            Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
              .clickable(onClick = onHighlight)
              .padding(horizontal = 10.dp),
          contentAlignment = Alignment.Center,
        ) {
          KanshuText(
            text = stringResource(R.string.reader_selection_highlight),
            style = KanshuTheme.typography.bodySmall,
            maxLines = 1,
          )
        }
      }
    }
  }
}

internal data class ReaderSelectionPopupPosition(val xPx: Int, val yPx: Int) {
  fun toIntOffset(): IntOffset = IntOffset(xPx, yPx)
}

internal fun readerSelectionPopupPosition(
  anchor: RectF,
  viewportWidthPx: Int,
  viewportHeightPx: Int,
  popupWidthPx: Int,
  popupHeightPx: Int,
  marginPx: Int,
): ReaderSelectionPopupPosition {
  val maxX = (viewportWidthPx - marginPx - popupWidthPx).coerceAtLeast(marginPx)
  val x = anchor.left.toInt().coerceIn(marginPx, maxX)
  val yAbove = anchor.top - popupHeightPx - marginPx
  val yBelow = anchor.bottom + marginPx
  val preferredY = if (yAbove >= marginPx) yAbove else yBelow
  val maxY = (viewportHeightPx - marginPx - popupHeightPx).coerceAtLeast(marginPx)
  val y = preferredY.toInt().coerceIn(marginPx, maxY)
  return ReaderSelectionPopupPosition(xPx = x, yPx = y)
}
