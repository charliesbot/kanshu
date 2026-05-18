package com.charliesbot.kanshu.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.designsystem.R
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme

@Composable
fun KanshuCheckBadge(contentDescription: String?, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .size(24.dp)
        .clip(CircleShape)
        .background(KanshuTheme.colors.onBackground)
        .border(1.dp, KanshuTheme.colors.background, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    KanshuIcon(
      painter = painterResource(R.drawable.check_24px),
      contentDescription = contentDescription,
      tint = KanshuTheme.colors.background,
      modifier = Modifier.size(16.dp),
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun KanshuCheckBadgePreview() {
  KanshuTheme { KanshuCheckBadge(contentDescription = null) }
}
