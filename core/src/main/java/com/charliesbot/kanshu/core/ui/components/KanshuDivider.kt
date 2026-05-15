package com.charliesbot.kanshu.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme

// Horizontal 1-pixel-by-default divider. Defaults to `border` color so it matches the rest of
// Kanshu's high-contrast surfaces. Pass `thickness` to thicken the line; pass a width-constraining
// modifier from the call site if you want the divider to span less than the full parent width.
@Composable
fun KanshuDivider(
  modifier: Modifier = Modifier,
  thickness: Dp = 1.dp,
  color: Color = KanshuTheme.colors.border,
) {
  Box(modifier.fillMaxWidth().height(thickness).background(color))
}
