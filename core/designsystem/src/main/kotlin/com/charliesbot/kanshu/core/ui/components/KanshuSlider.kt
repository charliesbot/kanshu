package com.charliesbot.kanshu.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.composeunstyled.UnstyledSlider

// Discrete slider for e-ink. Passing a non-zero `steps` value makes the underlying UnstyledSlider
// snap, so the track / thumb redraw only when the value crosses a notch boundary — continuous
// drag would refresh the e-ink panel constantly and ghost. Ticks are drawn on the track to make
// the snap points visible.
@Composable
fun KanshuSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  steps: Int = 0,
  enabled: Boolean = true,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  val active = if (enabled) KanshuTheme.colors.onBackground else KanshuTheme.colors.muted
  val inactive = KanshuTheme.colors.muted

  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    if (leading != null) {
      leading()
      Spacer(Modifier.width(16.dp))
    }
    UnstyledSlider(
      value = value,
      onValueChange = onValueChange,
      enabled = enabled,
      valueRange = valueRange,
      steps = steps,
      modifier = Modifier.weight(1f).height(40.dp),
      track = { state ->
        SliderTrack(
          fraction = state.fraction,
          steps = steps,
          activeColor = active,
          inactiveColor = inactive,
        )
      },
      thumb = { Box(Modifier.size(20.dp).clip(CircleShape).background(active)) },
    )
    if (trailing != null) {
      Spacer(Modifier.width(16.dp))
      trailing()
    }
  }
}

@Composable
private fun SliderTrack(fraction: Float, steps: Int, activeColor: Color, inactiveColor: Color) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
      val w = size.width
      val midY = size.height / 2f
      val trackThickness = 2.dp.toPx()
      // Inactive baseline runs the full width, active overlays from 0..fraction.
      drawLine(
        color = inactiveColor,
        start = Offset(0f, midY),
        end = Offset(w, midY),
        strokeWidth = trackThickness,
      )
      drawLine(
        color = activeColor,
        start = Offset(0f, midY),
        end = Offset(w * fraction, midY),
        strokeWidth = trackThickness,
      )
      if (steps > 0) {
        val stops = steps + 2 // include both endpoints
        val tickHalf = 4.dp.toPx()
        val tickWidth = 1.5.dp.toPx()
        for (i in 0 until stops) {
          val ratio = i.toFloat() / (stops - 1).toFloat()
          val x = w * ratio
          val color = if (ratio <= fraction) activeColor else inactiveColor
          drawLine(
            color = color,
            start = Offset(x, midY - tickHalf),
            end = Offset(x, midY + tickHalf),
            strokeWidth = tickWidth,
          )
        }
      }
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun KanshuSliderPreview() {
  KanshuTheme {
    Row(
      Modifier.fillMaxWidth().height(80.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      KanshuSlider(
        value = 0.4f,
        onValueChange = {},
        steps = 9,
        leading = { KanshuText(text = "A", style = KanshuTheme.typography.labelSmall) },
        trailing = { KanshuText(text = "A", style = KanshuTheme.typography.titleLarge) },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
