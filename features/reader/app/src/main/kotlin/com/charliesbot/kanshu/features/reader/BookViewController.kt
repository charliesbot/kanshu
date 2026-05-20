package com.charliesbot.kanshu.features.reader

import com.charliesbot.kanshu.core.reader.ReaderPreferences
import kotlinx.coroutines.flow.Flow
import org.readium.r2.shared.publication.Locator

/**
 * A controller representing the pagination and settings navigation contract of the book viewer.
 *
 * NOTE: This interface intentionally leaks Readium's [Locator] type. Under the "Option A"
 * architecture (swap navigator, keep streamer), we continue utilizing the Readium streamer to parse
 * and spine the publication. Thus, the navigation locator remains [Locator] across both the Readium
 * navigator (V0) and any future custom native navigators (V1).
 */
interface BookViewController {
  val currentLocator: Flow<Locator>

  fun go(locator: Locator): Boolean

  fun goForward(): Boolean

  fun goBackward(): Boolean

  fun submitPreferences(preferences: ReaderPreferences)
}
