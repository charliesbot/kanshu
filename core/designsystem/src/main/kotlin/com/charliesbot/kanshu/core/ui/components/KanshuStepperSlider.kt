package com.charliesbot.kanshu.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.designsystem.R
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import kotlin.math.roundToInt

/**
 * A stepped slider flanked by −/+ stepper buttons — the e-ink-friendly value control. Discrete
 * button taps beat drag gestures on a slow-refresh panel; the slider remains for coarse jumps.
 *
 * The step buttons disable at the range bounds (visible state change, no ripple, per the e-ink
 * interaction rules).
 */
@Composable
fun KanshuStepperSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  valueRange: ClosedFloatingPointRange<Float>,
  step: Float,
  decreaseContentDescription: String,
  increaseContentDescription: String,
  modifier: Modifier = Modifier,
  label: String? = null,
) {
  val totalStops = ((valueRange.endInclusive - valueRange.start) / step).roundToInt() + 1
  val steps = (totalStops - 2).coerceAtLeast(0)

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (label != null) {
      KanshuText(text = label, style = KanshuTheme.typography.titleMedium)
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      val canDecrement = value > valueRange.start + STEP_EPSILON
      IconKanshuButton(
        onClick = {
          if (canDecrement) {
            onValueChange((value - step).coerceIn(valueRange))
          }
        },
        enabled = canDecrement,
      ) {
        KanshuIcon(
          painter = painterResource(R.drawable.remove_24px),
          contentDescription = decreaseContentDescription,
        )
      }

      KanshuSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = Modifier.weight(1f),
      )

      val canIncrement = value < valueRange.endInclusive - STEP_EPSILON
      IconKanshuButton(
        onClick = {
          if (canIncrement) {
            onValueChange((value + step).coerceIn(valueRange))
          }
        },
        enabled = canIncrement,
      ) {
        KanshuIcon(
          painter = painterResource(R.drawable.add_24px),
          contentDescription = increaseContentDescription,
        )
      }
    }
  }
}

private const val STEP_EPSILON = 0.0001f

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun KanshuStepperSliderPreview() {
  KanshuTheme {
    KanshuStepperSlider(
      value = 1.2f,
      onValueChange = {},
      valueRange = 0.5f..2f,
      step = 0.1f,
      decreaseContentDescription = "Decrease",
      increaseContentDescription = "Increase",
      label = "Line spacing",
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun KanshuStepperSliderLabellessPreview() {
  KanshuTheme {
    KanshuStepperSlider(
      value = 1f,
      onValueChange = {},
      valueRange = 0.5f..2f,
      step = 0.1f,
      decreaseContentDescription = "Decrease",
      increaseContentDescription = "Increase",
    )
  }
}
