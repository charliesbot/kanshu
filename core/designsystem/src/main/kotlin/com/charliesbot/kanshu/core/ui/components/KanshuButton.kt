package com.charliesbot.kanshu.core.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.core.ui.theme.LocalKanshuContentColor
import com.composeunstyled.UnstyledButton

@Composable
fun KanshuButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  // Press inversion is driven manually instead of via UnstyledButton's pressed* params:
  // those animate transitions, which ghosts on e-ink. Keep the swap instant.
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()

  val backgroundColor: Color
  val contentColor: Color
  val borderColor: Color
  when {
    !enabled -> {
      backgroundColor = KanshuTheme.colors.background
      contentColor = KanshuTheme.colors.muted
      borderColor = KanshuTheme.colors.muted
    }
    isPressed -> {
      backgroundColor = KanshuTheme.colors.background
      contentColor = KanshuTheme.colors.onBackground
      borderColor = KanshuTheme.colors.border
    }
    else -> {
      backgroundColor = KanshuTheme.colors.onBackground
      contentColor = KanshuTheme.colors.background
      borderColor = KanshuTheme.colors.border
    }
  }

  CompositionLocalProvider(LocalKanshuContentColor provides contentColor) {
    UnstyledButton(
      onClick = onClick,
      modifier = modifier.heightIn(min = 48.dp),
      enabled = enabled,
      shape = KanshuTheme.shapes.button,
      backgroundColor = backgroundColor,
      borderColor = borderColor,
      borderWidth = 1.dp,
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
      interactionSource = interactionSource,
    ) {
      KanshuText(text = text, style = KanshuTheme.typography.bodyLarge)
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun KanshuButtonPreview() {
  KanshuTheme { KanshuButton(text = "Save", onClick = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun KanshuButtonDisabledPreview() {
  KanshuTheme { KanshuButton(text = "Save", onClick = {}, enabled = false) }
}
