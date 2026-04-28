package com.charliesbot.kanshu.features.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun HomeScreen() {
    HomeContent()
}

@Composable
private fun HomeContent() {
    Text(text = "Home")
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeContent()
}
