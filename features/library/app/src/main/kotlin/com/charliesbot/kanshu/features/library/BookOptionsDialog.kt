package com.charliesbot.kanshu.features.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.charliesbot.kanshu.core.library.LibraryItem
import com.charliesbot.kanshu.core.ui.components.KanshuButton
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R

// Compose UI's Dialog is unstyled platform-level — no Material chrome, no enter/exit animation
// to worry about for e-ink. The content draws its own background + border to match the app's
// high-contrast surfaces.
@Composable
fun BookOptionsDialog(item: LibraryItem, onDelete: () -> Unit, onDismiss: () -> Unit) {
  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(KanshuTheme.colors.background)
          .border(1.dp, KanshuTheme.colors.border)
          .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      BasicText(
        text = item.title,
        style = KanshuTheme.typography.title.copy(color = KanshuTheme.colors.onBackground),
      )
      KanshuButton(
        text = stringResource(R.string.library_book_options_delete),
        onClick = onDelete,
        modifier = Modifier.fillMaxWidth(),
      )
      KanshuButton(
        text = stringResource(R.string.library_book_options_cancel),
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
