package com.charliesbot.kanshu.features.reader

import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KanshuHtmlSanitizerTest {

  @Before
  fun setUp() {
    mockkStatic(android.util.Log::class)
    every { android.util.Log.w(any(), any<String>()) } returns 0
    every { android.util.Log.e(any(), any<String>(), any()) } returns 0
  }

  @Test
  fun testPreservesWhitelistedElementsAndWrapsInShell() {
    val raw =
      """
      <html>
        <head>
          <title>Test Book</title>
        </head>
        <body>
          <div>
            <h1>Chapter 1</h1>
            <p>This is a <strong>valid</strong> paragraph.</p>
          </div>
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)

    assertTrue(result.contains("<!DOCTYPE html>"))
    assertTrue(result.contains("id=\"kanshu-page\""))
    assertTrue(result.contains("https://kanshu.invalid/__kanshu__/kanshu-reader.css"))
    assertTrue(result.contains("<h1>Chapter 1</h1>"))
    assertTrue(result.contains("<strong>valid</strong>"))
  }

  @Test
  fun testStripsScriptsAndUnsafeElements() {
    val raw =
      """
      <html>
        <body>
          <div>
            <script>alert('hack');</script>
            <iframe src="https://unsafe.site"></iframe>
            <a href="javascript:malicious()">Dangerous Link</a>
            <img src="https://remote.site/fig.png" onclick="run()" />
          </div>
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)

    assertFalse(result.contains("<script>"))
    assertFalse(result.contains("<iframe>"))
    assertFalse(result.contains("javascript:"))
    assertFalse(result.contains("onclick"))
    assertFalse(result.contains("https://remote.site"))
  }

  @Test
  fun testUnrecognizedTagDisplaysVisualErrorBadge() {
    val raw =
      """
      <html>
        <body>
          <custom-element>Hello, <em>World</em>!</custom-element>
          <center>Centered Text</center>
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)

    // Unrecognized tag should be transformed to the visual missing tag badge
    assertTrue(result.contains("[Error: Missing Tag &lt;custom-element&gt;]"))
    assertTrue(result.contains("[Error: Missing Tag &lt;center&gt;]"))
    assertTrue(result.contains("border: 1px dashed #d32f2f; background: #ffebee; color: #d32f2f;"))

    // Content inside unrecognized tags must be preserved
    assertTrue(result.contains("Hello,"))
    assertTrue(result.contains("<em>World</em>"))
    assertTrue(result.contains("Centered Text"))
  }

  @Test
  fun testSanitizesBlockAndInlineCss() {
    val raw =
      """
      <html>
        <head>
          <style>
            p { color: red; background: url('https://remote.site/img.png'); }
            div { @import url('http://evil.site/style.css'); margin: 10px; }
          </style>
        </head>
        <body>
          <p style="color: blue; expression(alert('hack')); background: url(javascript:alert(1));">Inline Styled Text</p>
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)

    // The remote URL in CSS background-image should be stripped
    assertFalse(result.contains("https://remote.site"))
    // The @import should be stripped
    assertFalse(result.contains("@import"))
    // Safe standard CSS declarations should be preserved
    assertTrue(result.contains("color: red"))
    assertTrue(result.contains("margin: 10px"))

    // Inline style should be cleaned
    assertTrue(result.contains("color: blue"))
    assertFalse(result.contains("expression("))
    assertFalse(result.contains("javascript:"))
  }
}
