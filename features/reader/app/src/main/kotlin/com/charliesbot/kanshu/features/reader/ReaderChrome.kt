package com.charliesbot.kanshu.features.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.navigator.ReaderLayoutDiagnostics
import com.charliesbot.kanshu.navigator.model.ParseDiagnostics
import com.charliesbot.kanshu.strings.R

@Composable
internal fun ReaderChrome(
  title: String,
  currentPage: Int,
  pageCount: Int,
  overlayVisible: Boolean,
  readerPrefsVisible: Boolean,
  preferences: ReaderPreferences,
  parseDiagnostics: ParseDiagnostics,
  layoutDiagnostics: ReaderLayoutDiagnostics?,
  preferencesCallbacks: ReaderPrefsCallbacks,
  onOpenReaderPrefs: () -> Unit,
  onDismissOverlay: () -> Unit,
  onDismissReaderPrefs: () -> Unit,
) {
  if (overlayVisible) {
    ReaderOverlay(
      title = title,
      pageLabel =
        stringResource(
          R.string.reader_overlay_page_label,
          currentPage.coerceIn(0, pageCount.coerceAtLeast(1) - 1) + 1,
          pageCount.coerceAtLeast(1),
        ),
      onOpenReaderPrefs = onOpenReaderPrefs,
      onDismiss = onDismissOverlay,
    )
  }
  ReaderPrefsBottomSheet(
    isOpen = readerPrefsVisible,
    onDismiss = onDismissReaderPrefs,
    prefs = preferences,
    callbacks = preferencesCallbacks,
    parseDiagnostics = parseDiagnostics,
    layoutDiagnostics = layoutDiagnostics,
  )
}
