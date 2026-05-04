package com.charliesbot.kanshu.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable data class KanshuShapes(val button: Shape, val input: Shape)

val DefaultKanshuShapes =
  KanshuShapes(button = RoundedCornerShape(4.dp), input = RoundedCornerShape(4.dp))
