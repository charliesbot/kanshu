package com.charliesbot.kanshu.features.reader

import org.readium.r2.shared.publication.Locator

// Snapshot of where the reader is in the book's chapter list, derived from the current Readium
// Locator. `title` is the current chapter's TOC title (null if the locator doesn't match any TOC
// entry — e.g. front-matter pages outside the TOC). `prevLocator` / `nextLocator` are the
// destinations for chapter-jump buttons; either is null at the edges of the book.
data class ChapterState(val title: String?, val prevLocator: Locator?, val nextLocator: Locator?) {
  companion object {
    val Empty = ChapterState(title = null, prevLocator = null, nextLocator = null)
  }
}
