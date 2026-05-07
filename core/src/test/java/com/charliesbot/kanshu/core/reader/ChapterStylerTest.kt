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
}
