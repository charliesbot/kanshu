package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.charliesbot.kanshu.core.sync.RemoteProgress
import com.charliesbot.kanshu.core.ui.components.KanshuButton
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R

// Shown on book open when the sync layer found a remote position written after our local one.
// Lets the user decide whether to jump there instead of opening at the local resume point. No
// animation — matches BookOptionsDialog and the rest of the e-ink-friendly UI.
@Composable
fun RemoteProgressPrompt(suggestion: RemoteProgress, onApply: () -> Unit, onDismiss: () -> Unit) {
  val percent = (suggestion.percentage * 100).toInt().coerceIn(0, 100)
  val deviceName = suggestion.deviceName?.takeIf { it.isNotBlank() }
  val body =
    if (deviceName != null) {
      stringResource(R.string.reader_sync_prompt_body_with_device, deviceName, percent)
    } else {
      stringResource(R.string.reader_sync_prompt_body_no_device, percent)
    }
  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(KanshuTheme.colors.background)
          .border(1.dp, KanshuTheme.colors.border)
          .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      KanshuText(
        text = stringResource(R.string.reader_sync_prompt_title),
        style = KanshuTheme.typography.titleLarge,
      )
      KanshuText(text = body, style = KanshuTheme.typography.bodyLarge)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KanshuButton(
          text = stringResource(R.string.reader_sync_prompt_stay),
          onClick = onDismiss,
          modifier = Modifier.weight(1f),
        )
        KanshuButton(
          text = stringResource(R.string.reader_sync_prompt_apply),
          onClick = onApply,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}
