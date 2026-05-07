package com.charliesbot.kanshu.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme

// 2:3 is the typical book cover ratio, but Kavita serves whatever the source provides — pair this
// with ContentScale.Crop and accept that some covers will be edge-cropped.
private const val DEFAULT_COVER_ASPECT_RATIO = 2f / 3f

@Composable
fun KanshuCover(
  imageUrl: String?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  aspectRatio: Float = DEFAULT_COVER_ASPECT_RATIO,
) {
  Box(
    modifier =
      modifier
        .aspectRatio(aspectRatio)
        .background(KanshuTheme.colors.background)
        .border(1.dp, KanshuTheme.colors.border)
  ) {
    if (imageUrl != null) {
      AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun KanshuCoverPlaceholderPreview() {
  KanshuTheme { KanshuCover(imageUrl = null, contentDescription = null) }
}
