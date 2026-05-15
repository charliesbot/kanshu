package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.charliesbot.kanshu.core.ui.components.IconKanshuButton
import com.charliesbot.kanshu.core.ui.components.KanshuDivider
import com.charliesbot.kanshu.core.ui.components.KanshuIcon
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R

// Overlay chrome shown when the reader's center tap zone is hit. Owns its own top and bottom
// strips so ReaderScreen doesn't have to coordinate two siblings — the dismiss zone between them
// also lives here. Rendered with no animation (instant cut) per e-ink constraints.
@Composable
fun ReaderOverlay(
  title: String,
  chapterTitle: String?,
  prevChapterEnabled: Boolean,
  nextChapterEnabled: Boolean,
  onPrevChapter: () -> Unit,
  onNextChapter: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.fillMaxSize()) {
    OverlayTopBar(title = title)
    KanshuDivider(thickness = 2.dp)
    Box(Modifier.fillMaxWidth().weight(1f).clickable(onClick = onDismiss))
    KanshuDivider(thickness = 2.dp)
    OverlayBottomBar(
      chapterTitle = chapterTitle,
      prevEnabled = prevChapterEnabled,
      nextEnabled = nextChapterEnabled,
      onPrev = onPrevChapter,
      onNext = onNextChapter,
    )
  }
}

@Composable
private fun OverlayChromeBar(content: @Composable RowScope.() -> Unit) {
  Row(
    modifier = Modifier.height(60.dp).fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    content()
  }
}

@Composable
private fun OverlayTopBar(title: String) {
  Column(Modifier.background(KanshuTheme.colors.background)) {
    OverlayChromeBar {
      Row(modifier = Modifier.weight(1f)) {
        IconKanshuButton(onClick = {}) {
          KanshuIcon(
            painter = painterResource(com.charliesbot.kanshu.core.R.drawable.arrow_back_24px),
            contentDescription = stringResource(R.string.reader_overlay_back),
          )
        }
      }
      IconKanshuButton(onClick = {}) {
        KanshuIcon(
          painter = painterResource(com.charliesbot.kanshu.core.R.drawable.match_case_24px),
          contentDescription = stringResource(R.string.reader_overlay_typography),
        )
      }
      IconKanshuButton(onClick = {}) {
        KanshuIcon(
          painter =
            painterResource(com.charliesbot.kanshu.core.R.drawable.format_list_bulleted_24px),
          contentDescription = stringResource(R.string.reader_overlay_table_of_contents),
        )
      }
      IconKanshuButton(onClick = {}) {
        KanshuIcon(
          painter = painterResource(com.charliesbot.kanshu.core.R.drawable.search_24px),
          contentDescription = stringResource(R.string.reader_overlay_search),
        )
      }
      IconKanshuButton(onClick = {}) {
        KanshuIcon(
          painter = painterResource(com.charliesbot.kanshu.core.R.drawable.more_vert_24px),
          contentDescription = stringResource(R.string.reader_overlay_more),
        )
      }
    }
    KanshuDivider()
    OverlayChromeBar {
      KanshuText(
        text = title,
        modifier = Modifier.padding(horizontal = 16.dp),
        style = KanshuTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun OverlayBottomBar(
  chapterTitle: String?,
  prevEnabled: Boolean,
  nextEnabled: Boolean,
  onPrev: () -> Unit,
  onNext: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .height(72.dp)
        .background(KanshuTheme.colors.background)
        .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconKanshuButton(onClick = onPrev, enabled = prevEnabled) {
      KanshuIcon(
        painter = painterResource(com.charliesbot.kanshu.core.R.drawable.skip_previous_24px),
        contentDescription = stringResource(R.string.reader_overlay_previous_chapter),
      )
    }
    KanshuText(
      text = chapterTitle.orEmpty(),
      modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
      style = KanshuTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    IconKanshuButton(onClick = onNext, enabled = nextEnabled) {
      KanshuIcon(
        painter = painterResource(com.charliesbot.kanshu.core.R.drawable.skip_next_24px),
        contentDescription = stringResource(R.string.reader_overlay_next_chapter),
      )
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReaderOverlayPreview() {
  KanshuTheme {
    ReaderOverlay(
      title = "Conspirituality: How New Age Conspiracy Theories Became a Health Threat",
      chapterTitle = "Chapter 3",
      prevChapterEnabled = true,
      nextChapterEnabled = true,
      onPrevChapter = {},
      onNextChapter = {},
      onDismiss = {},
    )
  }
}
