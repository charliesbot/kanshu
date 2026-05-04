package com.charliesbot.kanshu.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class KanshuColors(
  val background: Color,
  val onBackground: Color,
  val border: Color,
  val muted: Color,
)

val LightKanshuColors =
  KanshuColors(
    background = Color.White,
    onBackground = Color.Black,
    border = Color.Black,
    muted = Color(0xFF777777),
  )
