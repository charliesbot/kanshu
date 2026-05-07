package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R

// Placeholder. The real reader will introduce tap-zone chrome (top reveals overlay; sides
// turn pages); per the PRD, reading mode defaults to zero persistent app UI, so this screen
// intentionally has no visible back affordance — system back is sufficient for now.
@Composable
fun ReaderScreen(seriesId: Int, title: String) {
  KanshuScaffold {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      BasicText(
        text = title,
        style = KanshuTheme.typography.title.copy(color = KanshuTheme.colors.onBackground),
      )
      BasicText(
        text = stringResource(R.string.reader_placeholder_series_id, seriesId),
        style = KanshuTheme.typography.body.copy(color = KanshuTheme.colors.onBackground),
      )
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReaderScreenPreview() {
  KanshuTheme { ReaderScreen(seriesId = 42, title = "The Pillars of the Earth") }
}
