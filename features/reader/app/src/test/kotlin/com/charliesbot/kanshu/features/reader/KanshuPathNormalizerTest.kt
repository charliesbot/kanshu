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
  fun testPlusPreservation() {
    // Both raw plus and encoded plus must be preserved, NOT turned into spaces
    assertEquals(
      "OEBPS/file+name.xhtml",
      KanshuPathNormalizer.normalizeAndRejectTraversal("/OEBPS/file+name.xhtml"),
    )
    assertEquals(
      "OEBPS/file+name.xhtml",
      KanshuPathNormalizer.normalizeAndRejectTraversal("/OEBPS/file%2Bname.xhtml"),
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
    // Normal resolving segment inside bounds
    assertEquals(
      "chapter1.xhtml",
      KanshuPathNormalizer.normalizeAndRejectTraversal("/OEBPS/../chapter1.xhtml"),
    )
    // Traverse beyond root (should be rejected and return null)
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal("../chapter1.xhtml"))
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal("OEBPS/../../chapter1.xhtml"))
  }

  @Test
  fun testRejectDoubleEncodingAttacks() {
    // Double percent-encoded dot dot: %252e%252e -> %2e%2e
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal("OEBPS/%252e%252e/chapter1.xhtml"))
    // Encoded dot dot: %2e%2e
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal("OEBPS/%2e%2e/chapter1.xhtml"))
    // Double encoded slash: %252f -> %2f
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal("OEBPS%252fchapter1.xhtml"))
    // Encoded slash: %2f
    assertNull(KanshuPathNormalizer.normalizeAndRejectTraversal("OEBPS%2fchapter1.xhtml"))
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
