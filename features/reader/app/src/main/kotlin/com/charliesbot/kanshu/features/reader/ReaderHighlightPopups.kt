package com.charliesbot.kanshu.features.reader

import androidx.compose.runtime.Composable
import com.charliesbot.kanshu.core.reader.ReaderHighlightColor
import com.charliesbot.kanshu.navigator.ReaderHighlightTap
import com.charliesbot.kanshu.navigator.ReaderSelectionInfo

@Composable
internal fun ReaderHighlightPopups(
  selectedText: ReaderSelectionInfo?,
  tappedHighlight: ReaderHighlightTap?,
  onHighlightAdded: (ReaderSelectionInfo, ReaderHighlightColor) -> Unit,
  onHighlightRemoved: (ReaderHighlightTap) -> Unit,
  onHighlightColorChanged: (ReaderHighlightTap, ReaderHighlightColor) -> Unit,
) {
  selectedText?.let { selection ->
    ReaderSelectionPopup(
      anchor = selection.anchor,
      currentColor = null,
      onDelete = null,
      onColorSelected = { color -> onHighlightAdded(selection, color) },
    )
  }
  tappedHighlight?.let { tap ->
    ReaderSelectionPopup(
      anchor = tap.anchor,
      currentColor = tap.highlight.color,
      onDelete = { onHighlightRemoved(tap) },
      onColorSelected = { color -> onHighlightColorChanged(tap, color) },
    )
  }
}
