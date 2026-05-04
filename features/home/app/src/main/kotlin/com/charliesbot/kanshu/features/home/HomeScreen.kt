package com.charliesbot.kanshu.features.home

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(viewModel: HomeViewModel = koinViewModel()) {
  val uiState by viewModel.uiState.collectAsState()
  HomeContent(uiState = uiState)
}

@Composable
private fun HomeContent(uiState: HomeUiState) {
  BasicText(
    text = uiState.title,
    style = KanshuTheme.typography.body.copy(color = KanshuTheme.colors.onBackground),
  )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun HomeScreenPreview() {
  KanshuTheme { HomeContent(uiState = HomeUiState()) }
}
