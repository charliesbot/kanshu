package com.charliesbot.kanshu.features.reader

import com.charliesbot.kanshu.core.reader.ReaderPreferences
import kotlinx.coroutines.flow.Flow
import org.readium.r2.shared.publication.Locator

interface BookViewController {
  val currentLocator: Flow<Locator>

  fun go(locator: Locator): Boolean

  fun goForward(): Boolean

  fun goBackward(): Boolean

  fun submitPreferences(preferences: ReaderPreferences)
}
