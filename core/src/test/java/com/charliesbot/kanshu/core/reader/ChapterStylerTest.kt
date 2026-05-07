package com.charliesbot.kanshu.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterStylerTest {

  @Test
  fun `removes link rel stylesheet tags`() {
    val raw =
      """
      <html><head><link rel="stylesheet" href="evil.css"/></head><body><p>Hi</p></body></html>
      """
        .trimIndent()
        .toByteArray()

    val out = ChapterStyler.style(raw)

    assertFalse("publisher stylesheet link should be gone", out.contains("evil.css"))
    assertFalse(out.contains("rel=\"stylesheet\""))
  }

  @Test
  fun `removes inline style blocks`() {
    val raw =
      """
      <html><head><style>p { font-size: 8pt; color: #cccccc; }</style></head><body><p>Hi</p></body></html>
      """
        .trimIndent()
        .toByteArray()

    val out = ChapterStyler.style(raw)

    assertFalse("publisher 8pt rule should be gone", out.contains("8pt"))
    assertFalse(out.contains("#cccccc"))
  }

  @Test
  fun `removes inline style attributes`() {
    val raw =
      """
      <html><body><p style="font-size: 6pt; color: #999;">Hi</p></body></html>
      """
        .trimIndent()
        .toByteArray()

    val out = ChapterStyler.style(raw)

    assertFalse("inline 6pt should be gone", out.contains("6pt"))
    assertFalse(out.contains("color: #999"))
  }

  @Test
  fun `injects our stylesheet in the head`() {
    val raw = "<html><body><p>Hi</p></body></html>".toByteArray()

    val out = ChapterStyler.style(raw)

    assertTrue("our serif rule should be present", out.contains("font-family: serif"))
    assertTrue("our background rule should be present", out.contains("background: #ffffff"))
  }

  @Test
  fun `preserves semantic content`() {
    val raw =
      """
      <html><body>
        <h1>Chapter 1</h1>
        <p>Down the <em>rabbit hole</em>.</p>
      </body></html>
      """
        .trimIndent()
        .toByteArray()

    val out = ChapterStyler.style(raw)

    assertTrue(out.contains("Chapter 1"))
    assertTrue(out.contains("rabbit hole"))
    assertTrue("italic emphasis kept", out.contains("<em>"))
    assertTrue("heading kept", out.contains("<h1>"))
  }

  @Test
  fun `preserves anchor ids for in-document navigation`() {
    val raw =
      """
      <html><body><h2 id="ch3">Three</h2><p>...</p></body></html>
      """
        .trimIndent()
        .toByteArray()

    val out = ChapterStyler.style(raw)

    assertTrue("id attributes survive", out.contains("id=\"ch3\""))
  }

  @Test
  fun `removes link tags with uppercase rel value`() {
    val raw =
      """
      <html><head><link rel="STYLESHEET" href="loud.css"/></head><body><p>Hi</p></body></html>
      """
        .trimIndent()
        .toByteArray()

    val out = ChapterStyler.style(raw)

    assertFalse("uppercase STYLESHEET should be stripped", out.contains("loud.css"))
  }

  @Test
  fun `removes alternate stylesheet links`() {
    val raw =
      """
      <html><head><link rel="alternate stylesheet" href="dark.css"/></head><body><p>Hi</p></body></html>
      """
        .trimIndent()
        .toByteArray()

    val out = ChapterStyler.style(raw)

    assertFalse("alternate stylesheet should be stripped", out.contains("dark.css"))
  }

  @Test
  fun `removes multiple sibling stylesheets and style blocks`() {
    val raw =
      """
      <html><head>
        <link rel="stylesheet" href="a.css"/>
        <link rel="stylesheet" href="b.css"/>
        <style>p { color: red; }</style>
        <style>h1 { font-size: 6pt; }</style>
      </head><body><p>x</p></body></html>
      """
        .trimIndent()
        .toByteArray()

    val out = ChapterStyler.style(raw)

    assertFalse(out.contains("a.css"))
    assertFalse(out.contains("b.css"))
    assertFalse(out.contains("color: red"))
    assertFalse(out.contains("6pt"))
  }

  @Test
  fun `output has exactly one style tag`() {
    val raw =
      """
      <html><head><style>p{color:red}</style></head><body><p style="color:red">x</p></body></html>
      """
        .trimIndent()
        .toByteArray()

    val out = ChapterStyler.style(raw)

    val styleTagCount = "<style>".toRegex().findAll(out).count()
    assertEquals(1, styleTagCount)
  }

  @Test
  fun `wraps body content in pagination host`() {
    val raw =
      """
      <html><body><h1>One</h1><p>two</p><p>three</p></body></html>
      """
        .trimIndent()
        .toByteArray()

    val out = ChapterStyler.style(raw)

    assertTrue("host wrapper present", out.contains("id=\"kanshu-page-host\""))
    // Original content lives inside the host now, not as direct body children.
    val hostBlock = out.substringAfter("id=\"kanshu-page-host\"")
    assertTrue("h1 moved into host", hostBlock.contains("<h1>One</h1>"))
    assertTrue("paragraphs moved into host", hostBlock.contains("two"))
  }

  @Test
  fun `injects pagination script that exposes kanshuGoToPage`() {
    val raw = "<html><body><p>x</p></body></html>".toByteArray()

    val out = ChapterStyler.style(raw)

    assertTrue("script tag present", out.contains("<script>"))
    assertTrue("page-count bridge call present", out.contains("Kanshu.onPageCount"))
    assertTrue("page navigator exposed", out.contains("kanshuGoToPage"))
  }

  @Test
  fun `script content is not html-escaped`() {
    val raw = "<html><body><p>x</p></body></html>".toByteArray()

    val out = ChapterStyler.style(raw)

    // The injected JS contains `<` (e.g. `count < 1`). If JSoup writes script as a TextNode the
    // `<` is HTML-encoded and the WebView's JS parser fails with SyntaxError. Verify raw chars
    // survive by spotting an entity that should never appear in a working script body.
    val scriptStart = out.indexOf("<script>")
    val scriptEnd = out.indexOf("</script>")
    assertTrue("script tag found", scriptStart >= 0 && scriptEnd > scriptStart)
    val scriptBody = out.substring(scriptStart, scriptEnd)
    assertFalse("less-than must not be escaped inside <script>", scriptBody.contains("&lt;"))
    assertFalse("greater-than must not be escaped inside <script>", scriptBody.contains("&gt;"))
  }

  @Test
  fun `injects column layout for paginated rendering`() {
    val raw = "<html><body><p>x</p></body></html>".toByteArray()

    val out = ChapterStyler.style(raw)

    assertTrue("column-width keyed off viewport", out.contains("column-width"))
    assertTrue("html overflow hidden so columns stay clipped", out.contains("overflow: hidden"))
  }
}
