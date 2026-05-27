package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderMargins
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R

@Composable
fun LayoutTab(
  margins: ReaderMargins,
  alignment: ReaderAlignment,
  onMarginsChange: (ReaderMargins) -> Unit,
  onAlignmentChange: (ReaderAlignment) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      KanshuText(
        text = stringResource(R.string.reader_prefs_margins),
        style = KanshuTheme.typography.titleMedium,
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ReaderMargins.entries.forEach { option ->
          MarginChip(
            option = option,
            selected = option == margins,
            onClick = { onMarginsChange(option) },
          )
        }
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      KanshuText(
        text = stringResource(R.string.reader_prefs_alignment),
        style = KanshuTheme.typography.titleMedium,
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ReaderAlignment.entries.forEach { option ->
          AlignmentChip(
            option = option,
            selected = option == alignment,
            onClick = { onAlignmentChange(option) },
          )
        }
      }
    }
  }
}

@Composable
private fun MarginChip(option: ReaderMargins, selected: Boolean, onClick: () -> Unit) {
  val contentDescription =
    when (option) {
      ReaderMargins.Compact -> stringResource(R.string.reader_prefs_margins_compact)
      ReaderMargins.Medium -> stringResource(R.string.reader_prefs_margins_medium)
      ReaderMargins.Wide -> stringResource(R.string.reader_prefs_margins_wide)
    }

  val shape = RoundedCornerShape(4.dp)
  Box(
    modifier =
      Modifier.width(74.dp)
        .height(48.dp)
        .background(Color.Transparent, shape)
        .border(if (selected) 3.dp else 1.dp, KanshuTheme.colors.onBackground, shape)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onClick,
        )
        .clearAndSetSemantics { this.contentDescription = contentDescription },
    contentAlignment = Alignment.Center,
  ) {
    val horizontalPadding =
      when (option) {
        ReaderMargins.Compact -> 8.dp
        ReaderMargins.Medium -> 14.dp
        ReaderMargins.Wide -> 20.dp
      }
    Column(
      modifier = Modifier.fillMaxWidth().padding(3.dp).padding(horizontal = horizontalPadding),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      repeat(4) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(KanshuTheme.colors.onBackground))
      }
    }
  }
}

@Composable
private fun AlignmentChip(option: ReaderAlignment, selected: Boolean, onClick: () -> Unit) {
  val contentDescription =
    when (option) {
      ReaderAlignment.Justify -> stringResource(R.string.reader_prefs_alignment_justify)
      ReaderAlignment.Left -> stringResource(R.string.reader_prefs_alignment_left)
    }

  val shape = RoundedCornerShape(4.dp)
  Box(
    modifier =
      Modifier.width(74.dp)
        .height(48.dp)
        .background(Color.Transparent, shape)
        .border(if (selected) 3.dp else 1.dp, KanshuTheme.colors.onBackground, shape)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onClick,
        )
        .clearAndSetSemantics { this.contentDescription = contentDescription },
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(3.dp).padding(horizontal = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      when (option) {
        ReaderAlignment.Justify -> {
          repeat(4) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(KanshuTheme.colors.onBackground))
          }
        }
        ReaderAlignment.Left -> {
          listOf(1f, 0.85f, 0.9f, 0.7f).forEach { multiplier ->
            Box(
              Modifier.fillMaxWidth(multiplier)
                .height(2.dp)
                .background(KanshuTheme.colors.onBackground)
            )
          }
        }
      }
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun LayoutTabPreview() {
  KanshuTheme {
    LayoutTab(
      margins = ReaderMargins.Medium,
      alignment = ReaderAlignment.Justify,
      onMarginsChange = {},
      onAlignmentChange = {},
    )
  }
}
