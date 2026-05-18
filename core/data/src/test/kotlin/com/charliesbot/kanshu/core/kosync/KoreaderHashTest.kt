package com.charliesbot.kanshu.core.kosync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KoreaderHashTest {

  @get:Rule val tempFolder = TemporaryFolder()

  // Vector lifted from Kavita's KoreaderHelperTests.HashContents_ValidFile_ReturnsExpectedHash.
  // Same EPUB, same expected hash — proves bit-for-bit parity with the server we sync against.
  @Test
  fun aesopsFablesMatchesKavitaVector() {
    val resource = javaClass.classLoader!!.getResourceAsStream("AesopsFables.epub")!!
    val target = tempFolder.newFile("AesopsFables.epub")
    target.outputStream().use { resource.copyTo(it) }

    assertEquals("8795ACA4BF264B57C1EEDF06A0CEE688", KoreaderHash.ofFile(target))
  }

  @Test
  fun missingFileReturnsNull() {
    assertNull(KoreaderHash.ofFile(File(tempFolder.root, "does-not-exist.epub")))
  }

  @Test
  fun emptyFileHashesToEmptyMd5() {
    val empty = tempFolder.newFile("empty.epub")
    // MD5 of zero bytes is d41d8cd98f00b204e9800998ecf8427e.
    assertEquals("D41D8CD98F00B204E9800998ECF8427E", KoreaderHash.ofFile(empty))
  }

  @Test
  fun fileSmallerThanSecondOffsetStillHashes() {
    val tiny = tempFolder.newFile("tiny.epub")
    tiny.writeBytes(ByteArray(512) { it.toByte() })
    // Only the first 512 bytes get fed to MD5; the next offset (1024) is past EOF and breaks the
    // loop.
    val expected =
      java.security.MessageDigest.getInstance("MD5")
        .digest(ByteArray(512) { it.toByte() })
        .joinToString("") { "%02X".format(it) }
    assertEquals(expected, KoreaderHash.ofFile(tiny))
  }
}
