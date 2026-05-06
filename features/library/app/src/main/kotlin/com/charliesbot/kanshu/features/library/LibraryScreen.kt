package com.charliesbot.kanshu.features.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.connection.KavitaCredentials
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = koinViewModel()) {
  val credentials by viewModel.credentials.collectAsState(initial = null)
  LibraryContent(credentials = credentials)
}

@Composable
private fun LibraryContent(credentials: KavitaCredentials?) {
  KanshuScaffold {
    Column(
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      BasicText(
        text = stringResource(R.string.library_title),
        style = KanshuTheme.typography.title.copy(color = KanshuTheme.colors.onBackground),
      )
      if (credentials != null) {
        BasicText(
          text = stringResource(R.string.library_connected_to, credentials.baseUrl),
          style = KanshuTheme.typography.body.copy(color = KanshuTheme.colors.onBackground),
        )
      }
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun LibraryScreenPreview() {
  KanshuTheme {
    LibraryContent(credentials = KavitaCredentials("https://kavita.example.com", "abc123"))
  }
}
