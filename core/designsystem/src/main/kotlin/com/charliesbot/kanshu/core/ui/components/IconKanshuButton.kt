package com.charliesbot.kanshu.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.designsystem.R
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.core.ui.theme.LocalKanshuContentColor
import com.composeunstyled.UnstyledButton

// Ghost icon button modeled after Material's IconButton. Idle is transparent + black content;
// pressed inverts to a solid black square with white content. Disabled keeps the transparent
// background and mutes the content. Content color is published via LocalKanshuContentColor so
// inner KanshuIcon (and any KanshuText) picks up the right state color without an explicit tint
// parameter. defaultMinSize gives an icon-only button a 48dp touch target while still allowing
// icon+text content to grow horizontally.
@Composable
fun IconKanshuButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()

  val backgroundColor: Color
  val contentColor: Color
  when {
    !enabled -> {
      backgroundColor = Color.Transparent
      contentColor = KanshuTheme.colors.muted
    }
    isPressed -> {
      backgroundColor = KanshuTheme.colors.onBackground
      contentColor = KanshuTheme.colors.background
    }
    else -> {
      backgroundColor = Color.Transparent
      contentColor = KanshuTheme.colors.onBackground
    }
  }

  CompositionLocalProvider(LocalKanshuContentColor provides contentColor) {
    UnstyledButton(
      onClick = onClick,
      modifier =
        modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).background(backgroundColor),
      enabled = enabled,
      contentPadding = PaddingValues(12.dp),
      interactionSource = interactionSource,
    ) {
      content()
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun IconKanshuButtonIconOnlyPreview() {
  KanshuTheme {
    IconKanshuButton(onClick = {}) {
      KanshuIcon(painter = painterResource(R.drawable.search_24px), contentDescription = "Search")
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun IconKanshuButtonWithTextPreview() {
  KanshuTheme {
    IconKanshuButton(onClick = {}) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        KanshuIcon(painter = painterResource(R.drawable.bookmark_24px), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        KanshuText(text = "Bookmark", style = KanshuTheme.typography.labelLarge)
      }
    }
  }
}
