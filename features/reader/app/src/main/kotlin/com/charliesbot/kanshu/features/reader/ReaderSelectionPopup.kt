package com.charliesbot.kanshu.features.reader

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.reader.ReaderHighlightColor
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R

/** Kindle-style actions anchored beside a selection or an existing highlight. */
@Composable
internal fun ReaderSelectionPopup(
  anchor: RectF,
  currentColor: ReaderHighlightColor?,
  canDelete: Boolean,
  onDelete: () -> Unit,
  onColorSelected: (ReaderHighlightColor) -> Unit,
) {
  var showingPalette by remember(anchor, currentColor) { mutableStateOf(false) }
  BoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds()) {
    val density = LocalDensity.current
    val marginPx = with(density) { 8.dp.roundToPx() }
    val maxPopupWidth = (maxWidth - 16.dp).coerceAtLeast(48.dp)
    var popupWidthPx by remember { mutableStateOf(0) }
    var popupHeightPx by remember { mutableStateOf(0) }
    val shape = RoundedCornerShape(18.dp)

    Box(
      modifier =
        Modifier.offset {
            readerSelectionPopupPosition(
                anchor = anchor,
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
          .clip(shape)
          .background(KanshuTheme.colors.background)
          .border(1.dp, KanshuTheme.colors.border, shape)
          .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
      if (showingPalette) {
        HighlightPalette(
          currentColor = currentColor,
          onBack = { showingPalette = false },
          onColorSelected = onColorSelected,
        )
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (canDelete) {
            PopupAction(
              label = stringResource(R.string.reader_highlight_delete),
              onClick = onDelete,
            ) {
              KanshuText(text = "×", style = KanshuTheme.typography.titleLarge)
            }
          }
          PopupAction(
            label = stringResource(R.string.reader_selection_highlight),
            onClick = { showingPalette = true },
          ) {
            ColorSwatch(color = currentColor ?: ReaderHighlightColor.default, selected = false)
          }
        }
      }
    }
  }
}

@Composable
private fun HighlightPalette(
  currentColor: ReaderHighlightColor?,
  onBack: () -> Unit,
  onColorSelected: (ReaderHighlightColor) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    PopupAction(label = stringResource(R.string.reader_highlight_back), onClick = onBack) {
      KanshuText(text = "←", style = KanshuTheme.typography.titleLarge)
    }
    ReaderHighlightColor.entries.forEach { color ->
      PopupAction(label = color.label(), onClick = { onColorSelected(color) }) {
        ColorSwatch(color = color, selected = color == currentColor)
      }
    }
  }
}

@Composable
private fun PopupAction(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
  Column(
    modifier = Modifier.width(64.dp).sizeIn(minHeight = 64.dp).clickable(onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon() }
    KanshuText(text = label, style = KanshuTheme.typography.bodySmall, maxLines = 1)
  }
}

@Composable
private fun ColorSwatch(color: ReaderHighlightColor, selected: Boolean) {
  Box(
    modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(color.argb)),
    contentAlignment = Alignment.Center,
  ) {
    if (selected) {
      KanshuText(text = "✓", style = KanshuTheme.typography.bodyLarge)
    }
  }
}

@Composable
private fun ReaderHighlightColor.label(): String =
  stringResource(
    when (this) {
      ReaderHighlightColor.Aqua -> R.string.reader_highlight_aqua
      ReaderHighlightColor.Pink -> R.string.reader_highlight_pink
      ReaderHighlightColor.Orange -> R.string.reader_highlight_orange
      ReaderHighlightColor.Yellow -> R.string.reader_highlight_yellow
      ReaderHighlightColor.Green -> R.string.reader_highlight_green
    }
  )

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
