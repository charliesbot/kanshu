package com.charliesbot.kanshu.features.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun BookViewer(
  publication: Publication,
  initialLocator: Locator?,
  initialPreferences: ReaderPreferences,
  onControllerReady: (BookViewController) -> Unit,
  onCenterTap: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val factory =
    EpubNavigatorFactory(
      publication = publication,
      configuration = EpubNavigatorFactory.Configuration(defaults = EpubTypography.defaults),
    )

  val initialEpubPrefs = EpubTypography.toEpubPreferences(initialPreferences)

  EpubNavigatorHost(
    factory = factory,
    initialPreferences = initialEpubPrefs,
    onNavigatorReady = { fragment -> onControllerReady(ReadiumBookViewController(fragment)) },
    onCenterTap = onCenterTap,
    initialLocator = initialLocator,
    modifier = modifier,
  )
}
