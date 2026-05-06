package com.charliesbot.kanshu.features.connection

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.ui.components.KanshuButton
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ConnectionScreen(viewModel: ConnectionViewModel = koinViewModel()) {
  val uiState by viewModel.uiState.collectAsState()
  ConnectionContent(
    uiState = uiState,
    onBaseUrlChange = viewModel::onBaseUrlChange,
    onApiKeyChange = viewModel::onApiKeyChange,
    onTest = viewModel::onTest,
  )
}

@Composable
private fun ConnectionContent(
  uiState: ConnectionUiState,
  onBaseUrlChange: (String) -> Unit,
  onApiKeyChange: (String) -> Unit,
  onTest: () -> Unit,
) {
  KanshuScaffold {
    Column(
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      BasicText(
        text = "Connect to Kavita",
        style = KanshuTheme.typography.title.copy(color = KanshuTheme.colors.onBackground),
      )
      LabeledTextField(
        label = "Base URL",
        value = uiState.baseUrl,
        onValueChange = onBaseUrlChange,
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
      )
      LabeledTextField(
        label = "API key",
        value = uiState.apiKey,
        onValueChange = onApiKeyChange,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onTest() }),
      )
      KanshuButton(
        text = "Test connection",
        onClick = onTest,
        enabled = uiState.canTest,
        modifier = Modifier.fillMaxWidth(),
      )
      StatusText(uiState.status)
    }
  }
}

@Composable
private fun LabeledTextField(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    BasicText(
      text = label,
      style = KanshuTheme.typography.label.copy(color = KanshuTheme.colors.onBackground),
    )
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .border(1.dp, KanshuTheme.colors.border)
          .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
      BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = KanshuTheme.typography.body.copy(color = KanshuTheme.colors.onBackground),
        cursorBrush = SolidColor(KanshuTheme.colors.onBackground),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun StatusText(status: TestStatus) {
  val text =
    when (status) {
      TestStatus.Idle -> ""
      TestStatus.Testing -> "Testing…"
      is TestStatus.Success ->
        if (status.serverVersion != null) "Connected. Kavita ${status.serverVersion}."
        else "Connected."
      is TestStatus.Error -> status.message
    }
  if (text.isEmpty()) return
  BasicText(
    text = text,
    style = KanshuTheme.typography.body.copy(color = KanshuTheme.colors.onBackground),
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
  )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ConnectionScreenIdlePreview() {
  KanshuTheme {
    ConnectionContent(
      uiState = ConnectionUiState(),
      onBaseUrlChange = {},
      onApiKeyChange = {},
      onTest = {},
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ConnectionScreenSuccessPreview() {
  KanshuTheme {
    ConnectionContent(
      uiState =
        ConnectionUiState(
          baseUrl = "https://kavita.example.com",
          apiKey = "abc123",
          status = TestStatus.Success("0.7.14"),
        ),
      onBaseUrlChange = {},
      onApiKeyChange = {},
      onTest = {},
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ConnectionScreenErrorPreview() {
  KanshuTheme {
    ConnectionContent(
      uiState =
        ConnectionUiState(
          baseUrl = "https://kavita.example.com",
          apiKey = "abc123",
          status = TestStatus.Error("Invalid API key."),
        ),
      onBaseUrlChange = {},
      onApiKeyChange = {},
      onTest = {},
    )
  }
}
