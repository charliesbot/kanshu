package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.designsystem.R as DesignsystemR
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.ui.components.IconKanshuButton
import com.charliesbot.kanshu.core.ui.components.KanshuButton
import com.charliesbot.kanshu.core.ui.components.KanshuIcon
import com.charliesbot.kanshu.core.ui.components.KanshuSlider
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R
import kotlin.math.roundToInt

@Composable
fun SpacingTab(
  prefs: ReaderPreferences,
  onLineSpacingChange: (Float) -> Unit,
  onParagraphSpacingChange: (Float) -> Unit,
  onWordSpacingChange: (Float) -> Unit,
  onLetterSpacingChange: (Float) -> Unit,
  onResetSpacing: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    SpacingSliderRow(
      label = stringResource(R.string.reader_prefs_line_spacing),
      value = prefs.lineSpacing,
      onValueChange = onLineSpacingChange,
      valueRange = ReaderPreferences.LINE_SPACING_MIN..ReaderPreferences.LINE_SPACING_MAX,
      step = ReaderPreferences.LINE_SPACING_STEP,
    )

    SpacingSliderRow(
      label = stringResource(R.string.reader_prefs_paragraph_spacing),
      value = prefs.paragraphSpacing,
      onValueChange = onParagraphSpacingChange,
      valueRange = ReaderPreferences.PARAGRAPH_SPACING_MIN..ReaderPreferences.PARAGRAPH_SPACING_MAX,
      step = ReaderPreferences.PARAGRAPH_SPACING_STEP,
    )

    SpacingSliderRow(
      label = stringResource(R.string.reader_prefs_word_spacing),
      value = prefs.wordSpacing,
      onValueChange = onWordSpacingChange,
      valueRange = ReaderPreferences.WORD_SPACING_MIN..ReaderPreferences.WORD_SPACING_MAX,
      step = ReaderPreferences.WORD_SPACING_STEP,
    )

    SpacingSliderRow(
      label = stringResource(R.string.reader_prefs_character_spacing),
      value = prefs.letterSpacing,
      onValueChange = onLetterSpacingChange,
      valueRange = ReaderPreferences.LETTER_SPACING_MIN..ReaderPreferences.LETTER_SPACING_MAX,
      step = ReaderPreferences.LETTER_SPACING_STEP,
    )

    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      KanshuText(
        text = stringResource(R.string.reader_prefs_reset_all_spacing_label),
        style = KanshuTheme.typography.titleMedium,
      )
      KanshuButton(
        text = stringResource(R.string.reader_prefs_reset_to_default),
        onClick = onResetSpacing,
      )
    }
  }
}

@Composable
private fun SpacingSliderRow(
  label: String,
  value: Float,
  onValueChange: (Float) -> Unit,
  valueRange: ClosedFloatingPointRange<Float>,
  step: Float,
  modifier: Modifier = Modifier,
) {
  val totalStops = ((valueRange.endInclusive - valueRange.start) / step).roundToInt() + 1
  val steps = (totalStops - 2).coerceAtLeast(0)

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    KanshuText(text = label, style = KanshuTheme.typography.titleMedium)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      val canDecrement = value > valueRange.start + 0.0001f
      IconKanshuButton(
        onClick = {
          if (canDecrement) {
            onValueChange((value - step).coerceIn(valueRange))
          }
        },
        enabled = canDecrement,
      ) {
        KanshuIcon(
          painter = painterResource(DesignsystemR.drawable.remove_24px),
          contentDescription = stringResource(R.string.reader_prefs_decrease),
        )
      }

      KanshuSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = Modifier.weight(1f),
      )

      val canIncrement = value < valueRange.endInclusive - 0.0001f
      IconKanshuButton(
        onClick = {
          if (canIncrement) {
            onValueChange((value + step).coerceIn(valueRange))
          }
        },
        enabled = canIncrement,
      ) {
        KanshuIcon(
          painter = painterResource(DesignsystemR.drawable.add_24px),
          contentDescription = stringResource(R.string.reader_prefs_increase),
        )
      }
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun SpacingTabPreview() {
  KanshuTheme {
    SpacingTab(
      prefs = ReaderPreferences(),
      onLineSpacingChange = {},
      onParagraphSpacingChange = {},
      onWordSpacingChange = {},
      onLetterSpacingChange = {},
      onResetSpacing = {},
    )
  }
}
