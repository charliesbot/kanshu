package com.charliesbot.kanshu.navigator.engine

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.LinkSpan
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.StyledGroup
import com.charliesbot.kanshu.navigator.model.TextLeaf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SpanFlattenerTest {
  @Test
  fun flatten_styledGroup_appliesGroupStyleToChildren() {
    val text =
      SpanFlattener.flatten(
        ParagraphBlock(
          listOf(TextLeaf("Plain "), StyledGroup(InlineStyle.Italic, listOf(TextLeaf("grouped"))))
        )
      ) as Spanned

    assertEquals("Plain grouped", text.toString())
    val spans = text.getSpans("Plain ".length, text.length, StyleSpan::class.java)
    assertTrue(spans.any { it.style == Typeface.ITALIC })
  }

  @Test
  fun flatten_linkSpan_preservesHrefAsMetadataSpan() {
    val text =
      SpanFlattener.flatten(
        ParagraphBlock(
          listOf(TextLeaf("See "), LinkSpan("note.xhtml", listOf(TextLeaf("the note"))))
        )
      ) as Spanned

    assertEquals("See the note", text.toString())
    val spans = text.getSpans("See ".length, text.length, EpubLinkSpan::class.java)
    assertEquals(1, spans.size)
    assertEquals("note.xhtml", spans.single().href)
  }

  @Test
  fun flatten_linkSpan_underlinesLinkText() {
    val text =
      SpanFlattener.flatten(
        ParagraphBlock(
          listOf(TextLeaf("See "), LinkSpan("note.xhtml", listOf(TextLeaf("the note"))))
        )
      ) as Spanned

    val underlines = text.getSpans(0, text.length, UnderlineSpan::class.java)
    assertEquals(1, underlines.size)
    assertEquals("See ".length, text.getSpanStart(underlines.single()))
    assertEquals(text.length, text.getSpanEnd(underlines.single()))
  }
}
