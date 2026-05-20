package com.charliesbot.kanshu.features.reader

import com.charliesbot.kanshu.core.reader.ReaderPreferences
import kotlinx.coroutines.flow.Flow
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalReadiumApi::class)
class ReadiumBookViewController(private val fragment: EpubNavigatorFragment) : BookViewController {
  override val currentLocator: Flow<Locator>
    get() = fragment.currentLocator

  override fun go(locator: Locator): Boolean = fragment.go(locator)

  override fun goForward(): Boolean = fragment.goForward()

  override fun goBackward(): Boolean = fragment.goBackward()

  override fun submitPreferences(preferences: ReaderPreferences) {
    fragment.submitPreferences(EpubTypography.toEpubPreferences(preferences))
  }
}
