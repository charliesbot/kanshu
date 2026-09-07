package com.charliesbot.kanshu.core.database

import com.charliesbot.kanshu.core.reader.SourceElementPath
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceElementPathConverterTest {
  private val converter = SourceElementPathConverter()

  @Test
  fun sourcePathRoundTripsThroughItsExistingStorageFormat() {
    val path = SourceElementPath(listOf(0, 2, 1))

    val stored = converter.toStorageValue(path)

    assertEquals("[0,2,1]", stored)
    assertEquals(path, converter.fromStorageValue(stored))
  }
}
