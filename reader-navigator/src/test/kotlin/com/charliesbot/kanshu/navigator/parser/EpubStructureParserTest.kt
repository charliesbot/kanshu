package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.ListBlock
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.QuoteBlock
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.model.TextLeaf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubStructureParserTest {
  @Test
  fun parse_blankInput_returnsEmptyDocument() {
    val result = EpubParser.parse("   ")

    assertTrue(result.document.blocks.isEmpty())
    assertTrue(result.diagnostics.unsupportedBlockTags.isEmpty())
  }

  @Test
  fun parse_simpleParagraph_extractsTextAndLanguage() {
    val result = EpubParser.parse(loadFixture("simple-paragraph.xhtml"))

    assertEquals("en", result.document.language)
    assertEquals(
      listOf("The sky above the port was the color of television, tuned to a dead channel."),
      result.document.paragraphText(),
    )
    assertTrue(result.document.blocks.all { it is ParagraphBlock })
  }

  @Test
  fun parse_emphasis_preservesInlineStyles() {
    val block =
      EpubParser.parse(loadFixture("emphasis.xhtml")).document.blocks.single() as ParagraphBlock

    assertEquals(
      listOf(
        TextLeaf("Plain, "),
        TextLeaf("italic", InlineStyle.Italic),
        TextLeaf(", "),
        TextLeaf("bold", InlineStyle.Bold),
        TextLeaf(", and "),
        TextLeaf("both", InlineStyle.BoldItalic),
        TextLeaf("."),
      ),
      block.spans,
    )
  }

  @Test
  fun parse_nestedDivs_unwrapsStructuralWrappers() {
    val paragraphs = EpubParser.parse(loadFixture("nested-divs.xhtml")).document.paragraphText()

    assertEquals(listOf("Nested paragraph one.", "Inline-only wrapper."), paragraphs)
  }

  @Test
  fun parse_tableAndAside_preservesTextAndCountsUnsupportedBlocks() {
    val result = EpubParser.parse(loadFixture("table-aside.xhtml"))

    assertEquals(
      listOf(
        "Before the table.",
        "Cell one Cell two",
        "Margin note preserved.",
        "After structural loss.",
      ),
      result.document.paragraphText(),
    )
    assertEquals(1, result.diagnostics.unsupportedBlockTags["table"])
    assertEquals(1, result.diagnostics.unsupportedBlockTags["aside"])
  }

  @Test
  fun parse_blockquote_preservesQuoteChildren() {
    val result =
      EpubParser.parse(
        """
        <html>
          <body>
            <blockquote>
              <p>First quoted paragraph.</p>
              <p>Second <em>quoted</em> paragraph.</p>
            </blockquote>
            <p>After quote.</p>
          </body>
        </html>
        """
          .trimIndent()
      )

    val quote = result.document.blocks[0] as QuoteBlock
    assertEquals(
      listOf("First quoted paragraph.", "Second quoted paragraph."),
      ReaderDocument(quote.children).paragraphText(),
    )
    assertEquals(
      TextLeaf("quoted", InlineStyle.Italic),
      ((quote.children[1] as ParagraphBlock).spans[1]),
    )
    assertTrue(result.document.blocks[1] is ParagraphBlock)
  }

  @Test
  fun parse_lists_preservesListItems() {
    val result =
      EpubParser.parse(
        """
        <html>
          <body>
            <ol>
              <li>First item</li>
              <li><p>Second <em>item</em></p></li>
            </ol>
            <p>After list.</p>
          </body>
        </html>
        """
          .trimIndent()
      )

    val list = result.document.blocks[0] as ListBlock
    assertTrue(list.ordered)
    assertEquals(2, list.items.size)
    assertEquals(listOf("First item"), ReaderDocument(list.items[0].blocks).paragraphText())
    assertEquals(listOf("Second item"), ReaderDocument(list.items[1].blocks).paragraphText())
    assertEquals(
      TextLeaf("item", InlineStyle.Italic),
      ((list.items[1].blocks.single() as ParagraphBlock).spans[1]),
    )
    assertTrue(result.document.blocks[1] is ParagraphBlock)
  }

  @Test
  fun parse_blockquoteWithList_preservesNestedList() {
    val result =
      EpubParser.parse(
        """
        <html>
          <body>
            <blockquote>
              <ul>
                <li>Quoted list item</li>
              </ul>
            </blockquote>
          </body>
        </html>
        """
          .trimIndent()
      )

    val quote = result.document.blocks.single() as QuoteBlock
    val list = quote.children.single() as ListBlock
    assertEquals(
      listOf("Quoted list item"),
      ReaderDocument(list.items.single().blocks).paragraphText(),
    )
  }

  @Test
  fun parse_lineBreak_insertsNewlineInParagraph() {
    val result = EpubParser.parse("<html><body><p>Line one<br/>Line two</p></body></html>")

    assertEquals(listOf("Line one\nLine two"), result.document.paragraphText())
  }

  @Test
  fun parse_asideInDiv_preservesParagraphBoundaries() {
    val result = EpubParser.parse(loadFixture("aside-in-div.xhtml"))

    assertEquals("fr", result.document.language)
    assertEquals(
      listOf("First aside paragraph.", "Second aside paragraph."),
      result.document.paragraphText(),
    )
    assertEquals(1, result.diagnostics.unsupportedBlockTags["aside"])
  }

  @Test
  fun parse_blockNestedUnderInlineWrapper_promotesBlocks() {
    val result =
      EpubParser.parse(
        "<html><body><div><span><p>First.</p><p>Second.</p></span></div></body></html>"
      )

    assertEquals(listOf("First.", "Second."), result.document.paragraphText())
    assertTrue(result.diagnostics.unsupportedInlineTags.isEmpty())
  }

  @Test
  fun parse_unknownInlineTagAtDepth_staysInlineWithoutFragmenting() {
    // <q>/<cite>/<code>-style unknown inline tags must not trigger block promotion — that would
    // fragment a sentence into separate paragraphs.
    val result =
      EpubParser.parse("<html><body><div><span>Some <q>quoted</q> text</span></div></body></html>")

    assertEquals(listOf("Some quoted text"), result.document.paragraphText())
    assertEquals(mapOf("q" to 1), result.diagnostics.unsupportedInlineTags)
  }

  @Test
  fun parse_subAndSup_preservesTextWithInlineDiagnostics() {
    val result =
      EpubParser.parse("<html><body><p>H<sub>2</sub>O and x<sup>2</sup>.</p></body></html>")

    assertEquals(listOf("H2O and x2."), result.document.paragraphText())
    assertEquals(1, result.diagnostics.unsupportedInlineTags["sub"])
    assertEquals(1, result.diagnostics.unsupportedInlineTags["sup"])
  }

  @Test
  fun parse_bodyXmlLang_extractsLanguageWhenHtmlLangAbsent() {
    val result = EpubParser.parse("<html><body xml:lang=\"fr-CA\"><p>Bonjour</p></body></html>")

    assertEquals("fr-CA", result.document.language)
  }
}
