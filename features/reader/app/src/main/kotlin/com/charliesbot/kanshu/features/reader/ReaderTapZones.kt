package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun ReaderTapZones(
  onPrevious: () -> Unit,
  onCenter: () -> Unit,
  onNext: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier.fillMaxSize()) {
    TapZone(onClick = onPrevious, modifier = Modifier.weight(1f))
    TapZone(onClick = onCenter, modifier = Modifier.weight(1f))
    TapZone(onClick = onNext, modifier = Modifier.weight(1f))
  }
}

@Composable
private fun TapZone(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .fillMaxHeight()
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onClick,
        )
  )
}
