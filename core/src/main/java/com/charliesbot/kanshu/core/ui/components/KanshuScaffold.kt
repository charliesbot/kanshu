package com.charliesbot.kanshu.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme

@Composable
fun KanshuScaffold(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
  Box(
    modifier =
      modifier.fillMaxSize().background(KanshuTheme.colors.background).safeDrawingPadding(),
    content = content,
  )
}
