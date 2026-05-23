package com.charliesbot.kanshu.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KanshuPathNormalizerTest {

  @Test
  fun testNormalPath() {
    assertEquals(
      "OEBPS/chapter1.xhtml",
      KanshuPathNormalizer.normalizeAndRejectTraversal("/OEBPS/chapter1.xhtml"),
    )
    assertEquals(
      "chapter1.xhtml",
      KanshuPathNormalizer.normalizeAndRejectTraversal("chapter1.xhtml"),
    )
  }

  @Test
  fun testPercentDecoding() {
    assertEquals(
      "OEBPS/chapter 1.xhtml",
      KanshuPathNormalizer.normalizeAndRejectTraversal("/OEBPS/chapter%201.xhtml"),
    )
    assertEquals(
      "OEBPS/chapter+1.xhtml",
      KanshuPathNormalizer.normalizeAndRejectTraversal("/OEBPS/chapter%2B1.xhtml"),
    )
  }

  @Test
  fun testCollapseRepeatedSlashes() {
    assertEquals(
      "OEBPS/chapter1.xhtml",
      KanshuPathNormalizer.normalizeAndRejectTraversal("///OEBPS//chapter1.xhtml"),
    )
  }

  @Test
  fun testRejectPathTraversal() {
    // Traverse up but stays inside root (should be successfully resolved)
    assertEquals(
      "chapter1.xhtml",
      KanshuPathNormalizer.normalizeAndRejectTraversal("/OEBPS/../chapter1.xhtml"),
    )
    // Traverse beyond root (should be rejected and return null)
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal("../chapter1.xhtml"))
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal("OEBPS/../../chapter1.xhtml"))
  }

  @Test
  fun testRejectInvalidCharacters() {
    // Backslash
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal("/OEBPS\\chapter1.xhtml"))
    // Null byte
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal("/OEBPS/chapter1.xhtml\u0000"))
    // ISO Control
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal("/OEBPS/chapter1.xhtml\n"))
  }

  @Test
  fun testResolveRelativeSegments() {
    assertEquals(
      "OEBPS/chapter1.xhtml",
      KanshuPathNormalizer.normalizeAndRejectTraversal("/OEBPS/./chapter1.xhtml"),
    )
  }

  @Test
  fun testEmptyOrRootPath() {
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal(""))
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal("/"))
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal("///"))
  }
}
