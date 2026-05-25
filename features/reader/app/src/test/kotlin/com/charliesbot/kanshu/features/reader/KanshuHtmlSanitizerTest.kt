package com.charliesbot.kanshu.features.reader

import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KanshuHtmlSanitizerTest {

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
    // Verifies semantic main tag instead of div
    assertTrue(result.contains("<main id=\"kanshu-page\">"))
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
            <img src="//evil.site/tracker.png" />
          </div>
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)

    assertFalse(result.contains("<script>alert"))
    assertFalse(result.contains("<iframe>"))
    assertFalse(result.contains("javascript:"))
    assertFalse(result.contains("onclick"))
    assertFalse(result.contains("https://remote.site"))
    assertFalse(result.contains("//evil.site"))
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

  @Test
  fun testPreservesNestedMediaQueries() {
    val raw =
      """
      <html>
        <head>
          <style>
            @media screen and (min-width: 600px) {
              p { color: red; background: url('https://remote.site/img.png'); }
              span { font-size: 14px; }
            }
          </style>
        </head>
        <body>
          <p>Text</p>
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)

    // Nested media query should be preserved
    assertTrue(result.contains("@media screen and (min-width: 600px)"))
    // Dangerous background image should be sanitized inside media query
    assertFalse(result.contains("https://remote.site"))
    // Safe property inside media query must be preserved
    assertTrue(result.contains("color: red"))
    assertTrue(result.contains("font-size: 14px"))
  }

  @Test
  fun testNoHtmlEscapingInStyles() {
    val raw =
      """
      <html>
        <head>
          <style>
            div > p { color: blue; }
          </style>
        </head>
        <body>
          <p>Text</p>
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)

    // Verify > is NOT escaped to &gt; inside <style>
    assertTrue(result.contains("div > p"))
    assertFalse(result.contains("div &gt; p"))
  }

  @Test
  fun testSvgWhitelistingAndSilentStripping() {
    val raw =
      """
      <html>
        <body>
          <svg width="100" height="100">
            <g>
              <circle cx="50" cy="50" r="40" fill="red" />
              <path d="M 10 10 L 90 90" />
            </g>
            <script>alert('svg-hack');</script>
            <foreignObject>Unsafe SVG Block</foreignObject>
          </svg>
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)

    // Safe SVG children are preserved without missing tag badges
    assertTrue(result.contains("<svg"))
    assertTrue(result.contains("<circle"))
    assertTrue(result.contains("<path"))
    assertFalse(result.contains("[Error: Missing Tag &lt;circle&gt;]"))

    // Unsafe or unrecognized elements inside SVG must be silently stripped without a badge
    assertFalse(result.contains("alert('svg-hack')"))
    assertFalse(result.contains("<foreignObject>"))
    assertFalse(result.contains("[Error: Missing Tag &lt;foreignObject&gt;]"))
    assertFalse(result.contains("Unsafe SVG Block"))
  }

  @Test
  fun testPreservesImageStyleAttribute() {
    val raw =
      """
      <html>
        <body>
          <img src="kanshu.invalid/local.jpg" style="width: 100%; height: auto;" />
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)

    // Verifies style attribute is preserved on img tag
    assertTrue(result.contains("style=\"width: 100%; height: auto\""))
  }

  @Test
  fun testStylesheetOrdering() {
    val raw =
      """
      <html>
        <head>
          <link rel="stylesheet" href="publisher.css" />
        </head>
        <body>
          <p>Text</p>
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)

    // Verifies publisher.css comes BEFORE kanshu-reader.css
    val pubIndex = result.indexOf("publisher.css")
    val kanshuIndex = result.indexOf("kanshu-reader.css")
    assertTrue(pubIndex != -1)
    assertTrue(kanshuIndex != -1)
    assertTrue(pubIndex < kanshuIndex)
  }

  @Test
  fun testSvgUseHrefSanitization() {
    val raw =
      """
      <html>
        <body>
          <svg>
            <use href="javascript:alert(1)" xlink:href="javascript:alert(2)" />
            <use href="https://kanshu.invalid/local.svg#icon" xlink:href="https://kanshu.invalid/local.svg#icon" />
          </svg>
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)

    // Unsafe href/xlink:href must be stripped
    assertFalse(result.contains("javascript:"))
    // Safe href/xlink:href must be preserved
    assertTrue(result.contains("href=\"https://kanshu.invalid/local.svg#icon\""))
    assertTrue(result.contains("xlink:href=\"https://kanshu.invalid/local.svg#icon\""))
  }

  @Test
  fun testRejectsAbsoluteNonHttpSchemes() {
    val raw =
      """
      <html>
        <body>
          <a href="mailto:user@example.com">Mail Us</a>
          <a href="tel:+123456789">Call Us</a>
          <a href="file:///etc/passwd">File access</a>
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)

    assertFalse(result.contains("mailto:"))
    assertFalse(result.contains("tel:"))
    assertFalse(result.contains("file:"))
  }

  @Test
  fun testPreservesSvgAllowlistedNewElements() {
    val raw =
      """
      <html>
        <body>
          <svg>
            <clipPath id="clip"><rect width="10" height="10" /></clipPath>
            <mask><rect fill="white" /></mask>
            <filter id="f"><feGaussianBlur /></filter>
            <pattern id="p"><circle r="5" /></pattern>
            <image href="https://kanshu.invalid/img.png" />
          </svg>
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)
    val lowerResult = result.lowercase()

    assertTrue(lowerResult.contains("<clippath"))
    assertTrue(lowerResult.contains("<mask"))
    assertTrue(lowerResult.contains("<filter"))
    assertTrue(lowerResult.contains("<pattern"))
    assertTrue(lowerResult.contains("<image"))
  }

  @Test
  fun testFontFaceParsingAndSanitization() {
    val raw =
      """
      <html>
        <head>
          <style>
            @font-face {
              font-family: 'Open Dyslexic';
              src: url('../fonts/OpenDyslexic.otf') format('opentype');
              font-weight: normal;
              font-style: normal;
            }
          </style>
        </head>
        <body>
          <p>Dyslexic reader font</p>
        </body>
      </html>
      """
        .trimIndent()

    val result = KanshuHtmlSanitizer.sanitizeAndWrap(raw)

    // @font-face is preserved
    assertTrue(result.contains("@font-face"))
    // Inner declaration elements are preserved
    assertTrue(result.contains("font-family: 'Open Dyslexic'"))
    assertTrue(result.contains("url('../fonts/OpenDyslexic.otf')"))
  }

  @Test
  fun testPreferenceStyleInjectionDoesNotInjectBridgeScript() {
    val raw = "<html><body><p>Test</p></body></html>"
    val prefs = ReaderPreferences(font = ReaderFont.Bitter, fontScale = 1.5f)
    val result = KanshuHtmlSanitizer.sanitizeAndWrap(rawHtml = raw, prefs = prefs)

    // The trusted bridge is injected by Kotlin after WebView load, not written into the shell.
    assertFalse(result.contains("window.__kanshuChapterLoadId__"))
    assertFalse(result.contains("window.kanshu.repaginate"))
    assertFalse(result.contains("https://kanshu.invalid/__kanshu__/kanshu-reader.js"))

    // Verify preference CSS variables are injected in style block
    assertTrue(result.contains("--reader-font: \"Bitter-Kanshu\""))
    assertTrue(result.contains("--font-size: 27px")) // 18 * 1.5 = 27
    assertTrue(result.contains("--line-height: 1.4"))
  }
}
