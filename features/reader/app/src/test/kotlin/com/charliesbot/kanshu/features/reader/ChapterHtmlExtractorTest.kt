package com.charliesbot.kanshu.features.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterHtmlExtractorTest {
  @Test
  fun bodyHtmlKeepsReadableBodyContent() {
    val body = ChapterHtmlExtractor.bodyHtml("<html><body><h1>Title</h1><p>Hello</p></body></html>")

    assertTrue(body.contains("<h1>Title</h1>"))
    assertTrue(body.contains("<p>Hello</p>"))
  }

  @Test
  fun bodyHtmlStripsExecutableContent() {
    val body =
      ChapterHtmlExtractor.bodyHtml(
        "<html><body><p>Safe</p><script>alert('x')</script><iframe src='x'></iframe></body></html>"
      )

    assertTrue(body.contains("Safe"))
    assertFalse(body.contains("script"))
    assertFalse(body.contains("iframe"))
    assertFalse(body.contains("alert"))
  }
}
