package com.charliesbot.kanshu.navigator

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.charliesbot.kanshu.navigator.model.ReaderDocument

@Composable
fun ReaderPageViewer(
  document: ReaderDocument,
  currentPage: Int,
  onPageCount: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  TODO("Phase 0: implement native Canvas page renderer")
}
