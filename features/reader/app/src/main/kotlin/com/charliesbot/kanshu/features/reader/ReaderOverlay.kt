package com.charliesbot.kanshu.features.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.charliesbot.kanshu.core.ui.components.IconKanshuButton
import com.charliesbot.kanshu.core.ui.components.KanshuButton
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
  onSyncToFurthest: () -> Unit,
  onOpenReaderPrefs: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var moreMenuOpen by remember { mutableStateOf(false) }
  Column(modifier.fillMaxSize()) {
    OverlayTopBar(
      title = title,
      onOpenReaderPrefs = onOpenReaderPrefs,
      onMoreOptions = { moreMenuOpen = true },
    )
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
  if (moreMenuOpen) {
    MoreOptionsMenu(
      onSyncToFurthest = {
        moreMenuOpen = false
        onSyncToFurthest()
      },
      onDismiss = { moreMenuOpen = false },
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
private fun OverlayTopBar(title: String, onOpenReaderPrefs: () -> Unit, onMoreOptions: () -> Unit) {
  Column(Modifier.background(KanshuTheme.colors.background)) {
    OverlayChromeBar {
      Row(modifier = Modifier.weight(1f)) {
        IconKanshuButton(onClick = {}) {
          KanshuIcon(
            painter =
              painterResource(com.charliesbot.kanshu.core.designsystem.R.drawable.arrow_back_24px),
            contentDescription = stringResource(R.string.reader_overlay_back),
          )
        }
      }
      IconKanshuButton(onClick = onOpenReaderPrefs) {
        KanshuIcon(
          painter =
            painterResource(com.charliesbot.kanshu.core.designsystem.R.drawable.match_case_24px),
          contentDescription = stringResource(R.string.reader_overlay_typography),
        )
      }
      IconKanshuButton(onClick = {}) {
        KanshuIcon(
          painter =
            painterResource(
              com.charliesbot.kanshu.core.designsystem.R.drawable.format_list_bulleted_24px
            ),
          contentDescription = stringResource(R.string.reader_overlay_table_of_contents),
        )
      }
      IconKanshuButton(onClick = {}) {
        KanshuIcon(
          painter =
            painterResource(com.charliesbot.kanshu.core.designsystem.R.drawable.search_24px),
          contentDescription = stringResource(R.string.reader_overlay_search),
        )
      }
      IconKanshuButton(onClick = onMoreOptions) {
        KanshuIcon(
          painter =
            painterResource(com.charliesbot.kanshu.core.designsystem.R.drawable.more_vert_24px),
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
        painter =
          painterResource(com.charliesbot.kanshu.core.designsystem.R.drawable.skip_previous_24px),
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
        painter =
          painterResource(com.charliesbot.kanshu.core.designsystem.R.drawable.skip_next_24px),
        contentDescription = stringResource(R.string.reader_overlay_next_chapter),
      )
    }
  }
}

// The 3-dot menu surface. Using a Dialog (same pattern as BookOptionsDialog) keeps the chrome
// minimal and consistent — a floating anchored popup would need its own primitives in the
// design system, and the menu items are few enough that a centered card reads fine.
@Composable
private fun MoreOptionsMenu(onSyncToFurthest: () -> Unit, onDismiss: () -> Unit) {
  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(KanshuTheme.colors.background)
          .border(1.dp, KanshuTheme.colors.border)
          .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      KanshuButton(
        text = stringResource(R.string.reader_overlay_sync_to_furthest),
        onClick = onSyncToFurthest,
        modifier = Modifier.fillMaxWidth(),
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
      onSyncToFurthest = {},
      onOpenReaderPrefs = {},
      onDismiss = {},
    )
  }
}
