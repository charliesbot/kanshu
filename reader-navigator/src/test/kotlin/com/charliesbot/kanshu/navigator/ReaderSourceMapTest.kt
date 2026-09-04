package com.charliesbot.kanshu.navigator

import com.charliesbot.kanshu.core.reader.SourceElementPath
import com.charliesbot.kanshu.navigator.parser.EpubParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSourceMapTest {
  @Test
  fun `maps flattened offsets to nested element paths without counting comments or indentation`() {
    val result =
      EpubParser.parse(
        """
        <html><body>
          <!-- ignored -->
          <section><p>Alpha <em>beta</em>.</p></section>
        </body></html>
        """
          .trimIndent()
      )

    assertEquals(SourceElementPath(listOf(0, 0)), result.document.sourceMap.pathAt(0))
    assertEquals(SourceElementPath(listOf(0, 0, 0)), result.document.sourceMap.pathAt(7))
  }

  @Test
  fun `exposes same-tag sibling indexes ids children and literal normalized matches`() {
    val map =
      EpubParser.parse(
          """<html><body><div id="first"><p>One</p><p>Same   words</p></div><div><p>Same words</p></div></body></html>"""
        )
        .document
        .sourceMap
    val secondParagraph = SourceElementPath(listOf(0, 1))

    assertEquals(1, map.inspect(secondParagraph)?.sameTagSiblingIndex)
    assertEquals(secondParagraph, map.resolveChild(SourceElementPath(listOf(0)), 1))
    assertEquals(SourceElementPath(listOf(0)), map.resolveElementId("first"))
    assertEquals(
      3 until 13,
      map.findFirstLiteralMatch(secondParagraph, secondParagraph, "Same words"),
    )
    assertNull(map.findFirstLiteralMatch(secondParagraph, secondParagraph, "missing"))
  }

  @Test
  fun `selection endpoints resolve across blocks and remain independent of pagination`() {
    val document =
      EpubParser.parse(
          """<html><body><p>first</p><blockquote><p>second</p><p>third</p></blockquote></body></html>"""
        )
        .document

    assertEquals(SourceElementPath(listOf(0)), document.sourceMap.pathAt(1))
    assertEquals(SourceElementPath(listOf(1, 1)), document.sourceMap.pathAt(14))
  }

  @Test
  fun `table fallback text remains anchored to its source cells`() {
    val map =
      EpubParser.parse(
          """<html><body><table><tr><td>left</td><td>right</td></tr></table></body></html>"""
        )
        .document
        .sourceMap

    // Jsoup inserts the HTML-standard tbody element while parsing the table.
    assertEquals(SourceElementPath(listOf(0, 0, 0, 0)), map.pathAt(0))
    assertEquals(SourceElementPath(listOf(0, 0, 0, 1)), map.pathAt(5))
  }
}
