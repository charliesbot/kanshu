package com.charliesbot.kanshu.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme

// Cells take their width from the parent (typically a LazyVerticalGrid cell) and their height
// from the cover image's intrinsic aspect ratio. Different rows may have different heights;
// that's the price of never cropping or letterboxing the source. The placeholder branch falls
// back to 2:3 so a missing cover still occupies a sensible cell.
private const val PLACEHOLDER_ASPECT_RATIO = 2f / 3f

@Composable
fun KanshuCover(imageUrl: String?, contentDescription: String?, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .background(KanshuTheme.colors.background)
        .border(1.dp, KanshuTheme.colors.border)
  ) {
    if (imageUrl != null) {
      AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth(),
      )
    } else {
      Spacer(modifier = Modifier.fillMaxWidth().aspectRatio(PLACEHOLDER_ASPECT_RATIO))
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun KanshuCoverPlaceholderPreview() {
  KanshuTheme { KanshuCover(imageUrl = null, contentDescription = null) }
}
