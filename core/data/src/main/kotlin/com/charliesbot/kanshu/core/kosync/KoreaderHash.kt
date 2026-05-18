package com.charliesbot.kanshu.core.kosync

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

// Reproduces KOReader's `partialMD5` and Kavita's `KoreaderHelper.HashContents`. The hash
// identifies a book by its content without reading the whole file — we MD5 1KB chunks at a
// fixed set of offsets, capped at ~268MB worth of seeks. Any reader that follows the same
// algorithm produces the same hash for the same EPUB, which is what makes kosync's identity
// source-agnostic.
//
// The original C# uses `step << (2 * i)` for i = -1..9 inside a 32-bit int. With i = -1 that
// shift wraps to 0 in 32-bit due to count masking + overflow. We bake the resulting offsets
// in as constants rather than rely on shift-overflow semantics matching across languages.
//
// Validated against the AesopsFables.epub vector from Kavita's KoreaderHelperTests
// (KoreaderHashTest).
private val OFFSETS =
  longArrayOf(
    0L,
    1024L,
    4096L,
    16384L,
    65536L,
    262144L,
    1048576L,
    4194304L,
    16777216L,
    67108864L,
    268435456L,
  )

private const val CHUNK_SIZE = 1024

object KoreaderHash {
  // Returns the partial-MD5 as upper-case hex (matching Kavita's output), or null if the file
  // is absent. An empty file returns the MD5 of zero bytes per the algorithm's edge behavior.
  fun ofFile(file: File): String? {
    if (!file.exists()) return null
    val md = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(CHUNK_SIZE)
    RandomAccessFile(file, "r").use { raf ->
      val length = raf.length()
      for (offset in OFFSETS) {
        if (offset >= length) break
        raf.seek(offset)
        val bytesRead = raf.read(buffer)
        if (bytesRead <= 0) break
        md.update(buffer, 0, bytesRead)
      }
    }
    return md.digest().joinToString("") { "%02X".format(it) }
  }
}
