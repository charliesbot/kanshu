package com.charliesbot.kanshu.core.kosync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KoreaderPositionTest {

  @Test
  fun encodeProducesSpineTop() {
    assertEquals("/body/DocFragment[1].0", KoreaderPosition.encode(0))
    assertEquals("/body/DocFragment[5].0", KoreaderPosition.encode(4))
    assertEquals("/body/DocFragment[11].0", KoreaderPosition.encode(10))
  }

  // Vectors mirror Kavita's KoreaderHelperTests so we know we'd parse what their server sends.

  @Test
  fun decodesStandardFullXPath() {
    assertEquals(10, KoreaderPosition.decodeSpineIndex("/body/DocFragment[11]/body/div/a"))
    assertEquals(0, KoreaderPosition.decodeSpineIndex("/body/DocFragment[1]/body/div/p[40]"))
    assertEquals(4, KoreaderPosition.decodeSpineIndex("/body/DocFragment[5]/body/section/div[2]"))
  }

  @Test
  fun decodesXPathWithCharacterOffsets() {
    assertEquals(
      7,
      KoreaderPosition.decodeSpineIndex("/body/DocFragment[8]/body/div/p[28]/text().264"),
    )
    assertEquals(2, KoreaderPosition.decodeSpineIndex("/body/DocFragment[3]/body/h1/text().0"))
    assertEquals(5, KoreaderPosition.decodeSpineIndex("/body/DocFragment[6]/body/div/span.0"))
  }

  @Test
  fun decodesShortPaths() {
    assertEquals(9, KoreaderPosition.decodeSpineIndex("/body/DocFragment[10].0"))
    assertEquals(4, KoreaderPosition.decodeSpineIndex("/body/DocFragment[5]"))
    assertEquals(0, KoreaderPosition.decodeSpineIndex("/body/DocFragment[1]/body"))
  }

  @Test
  fun decodesHashFragmentFormats() {
    assertEquals(9, KoreaderPosition.decodeSpineIndex("#_doc_fragment_10"))
    assertEquals(0, KoreaderPosition.decodeSpineIndex("#_doc_fragment_1"))
    assertEquals(9, KoreaderPosition.decodeSpineIndex("#_doc_fragment10"))
    assertEquals(9, KoreaderPosition.decodeSpineIndex("#_doc_fragment_10_ some_anchor"))
  }

  @Test
  fun decodeReturnsNullForUndecodable() {
    // Numeric-only is Kavita's PDF/archive shape; we treat it as "not an EPUB position."
    assertNull(KoreaderPosition.decodeSpineIndex("5"))
    assertNull(KoreaderPosition.decodeSpineIndex(""))
    assertNull(KoreaderPosition.decodeSpineIndex("   "))
    assertNull(KoreaderPosition.decodeSpineIndex("garbage"))
  }

  @Test
  fun roundTripPreservesSpineIndex() {
    for (i in 0..50) {
      assertEquals(i, KoreaderPosition.decodeSpineIndex(KoreaderPosition.encode(i)))
    }
  }
}
