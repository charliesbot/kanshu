package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.BlockAlignment
import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.model.HorizontalRule
import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.LinkSpan
import com.charliesbot.kanshu.navigator.model.ListBlock
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.QuoteBlock
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.model.TextLeaf
import com.charliesbot.kanshu.navigator.parser.css.CssParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubHeadingParserTest {
  @Test
  fun parse_mixedStructure_preservesHeadingsAndUnwrapsUnsupportedStructure() {
    val result = EpubParser.parse(loadFixture("mixed-structure.xhtml"))

    assertEquals("es", result.document.language)
    assertTrue(result.document.blocks.first() is HeadingBlock)
    assertEquals(
      "Chapter Title",
      spanText((result.document.blocks.first() as HeadingBlock).spans.single()),
    )
    assertEquals(
      listOf("Closing paragraph with a 漢kan annotation."),
      result.document.paragraphText(),
    )
    val quote = result.document.blocks[1] as QuoteBlock
    assertEquals(listOf("Quoted text."), ReaderDocument(quote.children).paragraphText())
    val list = result.document.blocks[2] as ListBlock
    assertEquals(2, list.items.size)
    assertEquals(listOf("First item"), ReaderDocument(list.items[0].blocks).paragraphText())
    assertEquals(listOf("Second item"), ReaderDocument(list.items[1].blocks).paragraphText())
    assertEquals(1, result.diagnostics.unsupportedInlineTags["ruby"])
  }

  @Test
  fun parse_headingsAndRules_preservesSemanticBlocks() {
    val result =
      EpubParser.parse(
        """
        <html>
          <body>
            <h1>Chapter <em>One</em></h1>
            <p>Opening paragraph.</p>
            <hr/>
            <h3>Section</h3>
          </body>
        </html>
        """
          .trimIndent()
      )

    assertEquals(4, result.document.blocks.size)
    assertEquals(
      listOf(TextLeaf("Chapter "), TextLeaf("One", InlineStyle.Italic)),
      (result.document.blocks[0] as HeadingBlock).spans,
    )
    assertEquals(1, (result.document.blocks[0] as HeadingBlock).level)
    assertTrue(result.document.blocks[1] is ParagraphBlock)
    assertTrue(result.document.blocks[2] is HorizontalRule)
    assertEquals(3, (result.document.blocks[3] as HeadingBlock).level)
    assertTrue(result.diagnostics.unsupportedBlockTags["hr"] == null)
  }

  @Test
  fun parse_withStylesheet_promotesBlockSpansInsideHeading() {
    val sheet =
      CssParser.parse(
        """
        h2.CHAPTER { display: inline }
        span.CN { display: block; margin: 2em 0; text-align: center }
        span.CT { display: block; margin: 0 0 1em; text-align: center }
        """
          .trimIndent()
      )
    val result =
      EpubParser.parse(
        """
        <html><body>
          <h2 class="CHAPTER">
            <span epub:type="pagebreak"></span>
            <a href="#chapter">
              <span class="CN">CHAPTER 1</span>
              <span class="CT"><strong>PLAY</strong></span>
            </a>
          </h2>
        </body></html>
        """
          .trimIndent(),
        stylesheets = listOf(sheet),
      )

    val headings = result.document.blocks.map { it as HeadingBlock }
    assertEquals(2, headings.size)
    assertEquals(2, headings[0].level)
    assertEquals(2, headings[1].level)
    assertEquals(BlockAlignment.Center, headings[0].alignment)
    assertEquals(BlockAlignment.Center, headings[1].alignment)
    assertEquals(2f, headings[0].spacing?.marginTopEm)
    assertEquals(2f, headings[0].spacing?.marginBottomEm)
    assertEquals(0f, headings[1].spacing?.marginTopEm)
    assertEquals(1f, headings[1].spacing?.marginBottomEm)
    assertEquals(
      listOf(LinkSpan("#chapter", listOf(TextLeaf("CHAPTER 1")))),
      headings[0].spans,
    )
    assertEquals(
      listOf(LinkSpan("#chapter", listOf(TextLeaf("PLAY", InlineStyle.Bold)))),
      headings[1].spans,
    )
    assertEquals(
      mapOf("span.CN inside h2" to 1, "span.CT inside h2" to 1),
      result.diagnostics.stylingCensus.blockDisplayContextCounts,
    )
  }

  @Test
  fun parse_withStylesheet_mixedHeadingContentKeepsSingleBlockFallback() {
    val sheet = CssParser.parse("h1 span.title { display: block; text-align: center }")
    val result =
      EpubParser.parse(
        "<html><body><h1>Part <span class=\"title\">ONE</span> continued</h1></body></html>",
        stylesheets = listOf(sheet),
      )

    val heading = result.document.blocks.single() as HeadingBlock
    assertEquals("Part ONE continued", heading.spans.joinToString("") { spanText(it) })
    assertNull(heading.alignment)
  }

  @Test
  fun parse_withStylesheet_emptyPromotedHeadingDoesNotConsumeOuterSpacing() {
    val sheet = CssParser.parse("h1 { margin-top: 2em } h1 span { display: block }")
    val result =
      EpubParser.parse(
        "<html><body><h1><span></span><span>START</span></h1></body></html>",
        stylesheets = listOf(sheet),
      )

    val heading = result.document.blocks.single() as HeadingBlock
    assertEquals(2f, heading.spacing?.marginTopEm)
  }

  @Test
  fun parse_withStylesheet_nestedBlockHeadingKeepsSingleBlockFallback() {
    val sheet =
      CssParser.parse(
        "h2 { text-align: left } h2 span { display: block } span.wrapper { text-align: center }"
      )
    val result =
      EpubParser.parse(
        "<html><body><h2><span class=\"wrapper\"><span>CHAPTER 1</span><span>PLAY</span></span></h2></body></html>",
        stylesheets = listOf(sheet),
      )

    val heading = result.document.blocks.single() as HeadingBlock
    assertEquals("CHAPTER 1PLAY", heading.spans.joinToString("") { spanText(it) })
    assertEquals(BlockAlignment.Start, heading.alignment)
  }

  @Test
  fun parse_withStylesheet_promotedHeadingPreservesOuterSpacingAroundGroup() {
    val sheet =
      CssParser.parse("h1.group { margin: 1.5em 1em 0.5em } h1.group span { display: block }")
    val result =
      EpubParser.parse(
        "<html><body><h1 class=\"group\"><span>PART 1</span><span>START</span></h1></body></html>",
        stylesheets = listOf(sheet),
      )

    val headings = result.document.blocks.map { it as HeadingBlock }
    assertEquals(1.5f, headings[0].spacing?.marginTopEm)
    assertEquals(0f, headings[0].spacing?.marginBottomEm)
    assertEquals(0f, headings[1].spacing?.marginTopEm)
    assertEquals(0.5f, headings[1].spacing?.marginBottomEm)
    assertEquals(1f, headings[0].spacing?.marginStartEm)
    assertEquals(1f, headings[1].spacing?.marginEndEm)
  }
}
