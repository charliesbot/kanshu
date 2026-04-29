package com.charliesbot.kanshu.features.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(viewModel: HomeViewModel = koinViewModel()) {
  val uiState by viewModel.uiState.collectAsState()
  HomeContent(uiState = uiState)
}

@Composable
private fun HomeContent(uiState: HomeUiState) {
  Text(text = uiState.title)
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
  HomeContent(uiState = HomeUiState())
}
