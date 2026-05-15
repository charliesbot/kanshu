package com.charliesbot.kanshu.features.reader

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

@OptIn(ExperimentalReadiumApi::class)
class TocIndexTest {

  // Url() constructor calls android.net.Uri.parse, which throws on the JVM. Mock Url instances
  // with stubbed equality (path-based) so the matcher can compare them. removeFragment(),
  // removeQuery(), normalize() are members on Url; we make them no-ops since the test paths
  // don't carry fragments or queries.
  private fun fakeUrl(path: String): Url {
    val url = mockk<Url>()
    every { url.toString() } returns path
    every { url.hashCode() } returns path.hashCode()
    every { url.equals(any()) } answers
      {
        val other = firstArg<Any?>()
        other is Url && other.toString() == path
      }
    every { url.removeFragment() } returns url
    every { url.removeQuery() } returns url
    every { url.normalize() } returns url
    return url
  }

  private val coverUrl = fakeUrl("cover.xhtml")
  private val titleUrl = fakeUrl("title.xhtml")
  private val midTocUrl = fakeUrl("mini_toc.xhtml") // spine resource between TOC entries
  private val chapter1Url = fakeUrl("chapter1.xhtml")
  private val chapter2Url = fakeUrl("chapter2.xhtml")

  private fun fakeLink(url: Url, title: String? = null): Link =
    mockk(relaxed = true) {
      every { this@mockk.url(any()) } returns url
      every { children } returns emptyList()
      every { this@mockk.title } returns title
    }

  private fun fakePublication(toc: List<Link>, spine: List<Link>): Publication =
    mockk(relaxed = true) {
      every { tableOfContents } returns toc
      every { readingOrder } returns spine
      // locatorFromLink returns a Locator whose href matches the link's url. Tests assert
      // against these hrefs to confirm the right neighbor was picked.
      every { locatorFromLink(any()) } answers
        {
          val link = firstArg<Link>()
          mockk<Locator>(relaxed = true) { every { href } returns link.url() }
        }
    }

  private fun fakeLocator(href: Url): Locator =
    mockk(relaxed = true) { every { this@mockk.href } returns href }

  private val titleChapter = fakeLink(titleUrl, "Title Page")
  private val chapter1 = fakeLink(chapter1Url, "Chapter 1")
  private val chapter2 = fakeLink(chapter2Url, "Chapter 2")
  // Spine has cover + mini_toc as extra entries that aren't TOC anchors.
  private val toc = listOf(titleChapter, chapter1, chapter2)
  private val spine =
    listOf(fakeLink(coverUrl), titleChapter, fakeLink(midTocUrl), chapter1, chapter2)
  private val pub = fakePublication(toc, spine)
  private val index = TocIndex(pub)

  @Test
  fun `null locator returns Empty`() {
    assertEquals(ChapterState.Empty, index.chapterStateFor(null))
  }

  @Test
  fun `direct hit on a TOC anchor returns that chapter with neighbors`() {
    val state = index.chapterStateFor(fakeLocator(chapter1Url))
    assertEquals("Chapter 1", state.title)
    assertEquals(titleUrl, state.prevLocator?.href)
    assertEquals(chapter2Url, state.nextLocator?.href)
  }

  @Test
  fun `spine fallback resolves to the latest preceding TOC entry`() {
    // mini_toc is between Title Page and Chapter 1 in the spine. Should resolve to Title Page.
    val state = index.chapterStateFor(fakeLocator(midTocUrl))
    assertEquals("Title Page", state.title)
    assertNull(state.prevLocator)
    assertEquals(chapter1Url, state.nextLocator?.href)
  }

  @Test
  fun `cover before any TOC entry yields next equals first chapter`() {
    val state = index.chapterStateFor(fakeLocator(coverUrl))
    assertNull(state.title)
    assertNull(state.prevLocator)
    assertEquals(titleUrl, state.nextLocator?.href)
  }

  @Test
  fun `locator not in spine returns Empty`() {
    val unknownUrl = fakeUrl("not-in-publication.xhtml")
    assertEquals(ChapterState.Empty, index.chapterStateFor(fakeLocator(unknownUrl)))
  }

  @Test
  fun `last chapter has no next neighbor`() {
    val state = index.chapterStateFor(fakeLocator(chapter2Url))
    assertEquals("Chapter 2", state.title)
    assertEquals(chapter1Url, state.prevLocator?.href)
    assertNull(state.nextLocator)
  }

  @Test
  fun `first chapter has no prev neighbor`() {
    val state = index.chapterStateFor(fakeLocator(titleUrl))
    assertEquals("Title Page", state.title)
    assertNull(state.prevLocator)
    assertEquals(chapter1Url, state.nextLocator?.href)
  }

  @Test
  fun `empty TOC returns Empty for any locator`() {
    val emptyPub = fakePublication(toc = emptyList(), spine = listOf(fakeLink(coverUrl)))
    val emptyIndex = TocIndex(emptyPub)
    assertEquals(ChapterState.Empty, emptyIndex.chapterStateFor(fakeLocator(coverUrl)))
  }

  @Test
  fun `nested TOC is flattened depth-first`() {
    // Parent + child both pointing at chapter1. indexOfLast picks the deepest match, so a child
    // chapter wins over its parent at the same resource.
    val parent = fakeLink(chapter1Url, "Part 1")
    every { parent.children } returns listOf(chapter1)
    val nestedToc = listOf(parent, chapter2)
    val nestedSpine = listOf(chapter1, chapter2)
    val nestedPub = fakePublication(toc = nestedToc, spine = nestedSpine)
    val nestedIndex = TocIndex(nestedPub)
    val state = nestedIndex.chapterStateFor(fakeLocator(chapter1Url))
    assertEquals("Chapter 1", state.title)
  }
}
