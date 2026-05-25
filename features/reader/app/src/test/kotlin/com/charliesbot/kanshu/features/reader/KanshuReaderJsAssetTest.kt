package com.charliesbot.kanshu.features.reader

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanshuReaderJsAssetTest {

  private val script: String =
    listOf(
        File("src/main/assets/kanshu-reader.js"),
        File("features/reader/app/src/main/assets/kanshu-reader.js"),
      )
      .first { it.exists() }
      .readText()

  @Test
  fun `bridge reports read current load id at report time`() {
    assertTrue(script.contains("function chapterLoadId()"))
    assertTrue(script.contains("bridge.onPageSettled(chapterLoadId(), pageIndex, progress)"))
    assertTrue(script.contains("bridge.onRepaginated(chapterLoadId(), revision"))
    assertFalse(script.contains("const loadId = window.__kanshuChapterLoadId__"))
  }

  @Test
  fun `font readiness race callback is one shot`() {
    assertTrue(script.contains("let fired = false"))
    assertTrue(script.contains("if (fired) return"))
    assertTrue(script.contains("fired = true"))
  }

  @Test
  fun `page math uses floor index and ceil page count`() {
    assertTrue(script.contains("Math.floor(scrollX / viewportWidth)"))
    assertTrue(script.contains("Math.floor(targetScrollX / newViewportWidth)"))
    assertTrue(script.contains("Math.ceil(currentWidth / viewportWidth)"))
    assertTrue(script.contains("Math.ceil(currentWidth / newViewportWidth)"))
    assertFalse(script.contains("Math.round(scrollX / viewportWidth)"))
  }
}
