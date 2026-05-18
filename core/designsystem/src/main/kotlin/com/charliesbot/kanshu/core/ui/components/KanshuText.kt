package com.charliesbot.kanshu.core.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.core.ui.theme.LocalKanshuContentColor

// Theme-aware text primitive. Mirrors what Material's `Text` does over `BasicText`: if the given
// style has no color (Color.Unspecified), fall back to LocalKanshuContentColor — which
// KanshuTheme seeds with `colors.onBackground`. Surface-flipping components (buttons, selected
// rows) override the local in their subtree and KanshuText automatically picks up the new value.
// Color baked into the style at the call site still wins (escape hatch when needed).
@Composable
fun KanshuText(
  text: String,
  modifier: Modifier = Modifier,
  style: TextStyle = KanshuTheme.typography.bodyLarge,
  textAlign: TextAlign? = null,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
) {
  val resolvedStyle = run {
    var s = style
    if (s.color == Color.Unspecified) s = s.copy(color = LocalKanshuContentColor.current)
    if (textAlign != null) s = s.copy(textAlign = textAlign)
    s
  }
  BasicText(
    text = text,
    modifier = modifier,
    style = resolvedStyle,
    maxLines = maxLines,
    overflow = overflow,
  )
}
