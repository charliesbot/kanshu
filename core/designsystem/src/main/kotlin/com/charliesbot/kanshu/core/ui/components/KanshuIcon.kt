package com.charliesbot.kanshu.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.ui.theme.LocalKanshuContentColor

// Theme-aware icon. Tint resolves from LocalKanshuContentColor by default so press inversion
// in IconKanshuButton/KanshuButton flows through automatically. Default size is 24dp via
// defaultMinSize so the caller can still size up via `Modifier.size(...)`; matches Material's
// Icon sizing semantics.
@Composable
fun KanshuIcon(
  painter: Painter,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  tint: Color = LocalKanshuContentColor.current,
) {
  Image(
    painter = painter,
    contentDescription = contentDescription,
    modifier = modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp),
    colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint),
  )
}
