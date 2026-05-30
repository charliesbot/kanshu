package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.model.HorizontalRule
import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.model.StyledGroup
import com.charliesbot.kanshu.navigator.model.TextLeaf
import com.charliesbot.kanshu.navigator.model.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun ReaderDocument.paragraphText(): List<String> =
  blocks.filterIsInstance<ParagraphBlock>().map { block ->
    block.spans.joinToString("") { spanText(it) }
  }

internal fun spanText(span: TextSpan): String =
  when (span) {
    is TextLeaf -> span.text
    is StyledGroup -> span.children.joinToString("") { spanText(it) }
    else -> ""
  }

class EpubParserTest {
  private fun loadFixture(name: String): String {
    val classLoader = requireNotNull(javaClass.classLoader)
    return checkNotNull(classLoader.getResourceAsStream("fixtures/$name")) {
        "Missing fixture: $name"
      }
      .bufferedReader()
      .readText()
  }

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
  fun parse_mixedStructure_preservesHeadingsAndUnwrapsUnsupportedStructure() {
    val result = EpubParser.parse(loadFixture("mixed-structure.xhtml"))

    assertEquals("es", result.document.language)
    assertTrue(result.document.blocks.first() is HeadingBlock)
    assertEquals(
      "Chapter Title",
      spanText((result.document.blocks.first() as HeadingBlock).spans.single()),
    )
    assertEquals(
      listOf(
        "Quoted text.",
        "First item",
        "Second item",
        "Closing paragraph with a 漢kan annotation.",
      ),
      result.document.paragraphText(),
    )
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
  fun parse_lineBreak_insertsNewlineInParagraph() {
    val result = EpubParser.parse("<html><body><p>Line one<br/>Line two</p></body></html>")

    assertEquals(listOf("Line one\nLine two"), result.document.paragraphText())
  }

  @Test
  fun parse_link_preservesLinkTextWithoutLinkSpan() {
    val block =
      EpubParser.parse(
          "<html><body><p>See <a href=\"note.xhtml\">the note</a> here.</p></body></html>"
        )
        .document
        .blocks
        .single() as ParagraphBlock

    assertEquals(listOf(TextLeaf("See "), TextLeaf("the note"), TextLeaf(" here.")), block.spans)
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
  fun parse_inlineImage_preservesAltText() {
    val result =
      EpubParser.parse(
        "<html><body><p>Before <img alt=\"ornament\" src=\"ornament.png\"/> after.</p></body></html>"
      )

    assertEquals(listOf("Before ornament after."), result.document.paragraphText())
    assertEquals(1, result.diagnostics.unsupportedInlineTags["img"])
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
