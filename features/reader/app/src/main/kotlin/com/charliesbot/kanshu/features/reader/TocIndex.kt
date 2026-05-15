package com.charliesbot.kanshu.features.reader

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

// Pre-computed index over a Publication's TOC + spine, used to map a current Locator to "the
// chapter the user is currently reading" plus its prev/next neighbors.
//
// The matching strategy mirrors what Readium's own Manifest.linkWithHref does internally: every
// href is resolved via Link.url() and normalized (fragment + query stripped, scheme/path canonical)
// so we're comparing fully-resolved Urls on both sides instead of raw Href strings.
@OptIn(ExperimentalReadiumApi::class)
internal class TocIndex(private val publication: Publication) {
  // Flattened depth-first so nested TOC entries (parts → chapters → sections) get a linear order.
  private val chapters: List<Link> = flattenToc(publication.tableOfContents)
  // Each chapter's resolved+normalized Url. null when the link's href doesn't resolve.
  private val chapterUrls: List<Url?> = chapters.map { it.url().normalizeForMatch() }
  // Spine entries resolved the same way, in spine order.
  private val spineUrls: List<Url?> = publication.readingOrder.map { it.url().normalizeForMatch() }
  // Each chapter's index in the spine (or -1 if it doesn't appear there). Lets us recover "the
  // chapter containing this page" when a locator falls on a spine resource that isn't itself a
  // TOC anchor — front-matter, mid-chapter sub-resources, etc.
  private val chapterSpineIndices: List<Int> = chapterUrls.map { spineUrls.indexOf(it) }

  // Two-phase resolution:
  //   1. Direct TOC hit — locator's normalized Url matches a TOC entry. indexOfLast handles
  //      parent/child TOC entries pointing at the same resource (we pick the deepest one).
  //   2. Spine fallback — locator is on a spine resource that isn't a TOC anchor. Find the
  //      latest TOC entry whose spine position is at or before the locator's.
  // Edge: locator in the spine but ahead of every TOC entry (typical cover) returns title=null
  // with next=TOC[0] so the user can still jump into the book.
  fun chapterStateFor(locator: Locator?): ChapterState {
    if (locator == null || chapters.isEmpty()) return ChapterState.Empty
    val locatorUrl = locator.href.normalizeForMatch()

    var index = chapterUrls.indexOfLast { it != null && it == locatorUrl }
    if (index < 0) {
      val spineIndex = spineUrls.indexOfFirst { it != null && it == locatorUrl }
      if (spineIndex < 0) return ChapterState.Empty
      index = chapterSpineIndices.indexOfLast { it in 0..spineIndex }
      if (index < 0) {
        val firstToc = chapters.firstOrNull() ?: return ChapterState.Empty
        return ChapterState(
          title = null,
          prevLocator = null,
          nextLocator = publication.locatorFromLink(firstToc),
        )
      }
    }

    val current = chapters[index]
    val prev = chapters.getOrNull(index - 1)
    val next = chapters.getOrNull(index + 1)
    return ChapterState(
      title = current.title,
      prevLocator = prev?.let { publication.locatorFromLink(it) },
      nextLocator = next?.let { publication.locatorFromLink(it) },
    )
  }

  private fun Url?.normalizeForMatch(): Url? = this?.removeFragment()?.removeQuery()?.normalize()

  private fun flattenToc(links: List<Link>): List<Link> = buildList {
    for (link in links) {
      add(link)
      addAll(flattenToc(link.children))
    }
  }
}
