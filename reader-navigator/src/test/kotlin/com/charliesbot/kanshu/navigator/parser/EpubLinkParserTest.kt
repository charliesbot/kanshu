package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.LinkSpan
import com.charliesbot.kanshu.navigator.model.ListBlock
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.TextLeaf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubLinkParserTest {
  @Test
  fun parse_link_preservesHrefAsLinkSpan() {
    val block =
      EpubParser.parse(
          "<html><body><p>See <a href=\"note.xhtml\">the note</a> here.</p></body></html>"
        )
        .document
        .blocks
        .single() as ParagraphBlock

    assertEquals(
      listOf(
        TextLeaf("See "),
        LinkSpan("note.xhtml", listOf(TextLeaf("the note"))),
        TextLeaf(" here."),
      ),
      block.spans,
    )
  }

  @Test
  fun parse_navListWithInlineTags_preservesListItemSemanticsWithoutDiagnostics() {
    val result =
      EpubParser.parse(
        """
        <html>
          <body>
            <nav>
              <ol>
                <li><a href="chapter.xhtml"><span><em>Chapter One</em></span></a></li>
              </ol>
            </nav>
          </body>
        </html>
        """
          .trimIndent()
      )

    val list = result.document.blocks.single() as ListBlock
    val item = list.items.single().blocks.single() as ParagraphBlock
    assertEquals(
      listOf(LinkSpan("chapter.xhtml", listOf(TextLeaf("Chapter One", InlineStyle.Italic)))),
      item.spans,
    )
    assertTrue(result.diagnostics.unsupportedBlockTags.isEmpty())
    assertTrue(result.diagnostics.unsupportedInlineTags.isEmpty())
  }

  @Test
  fun parse_linkHrefs_resolveAgainstBasePreservingFragments() {
    val result =
      EpubParser.parse(
        """
        <html><body><p>
          <a href="../text/ch02.xhtml#s3">next</a>
          <a href="#note1">note</a>
          <a href="https://example.com/x">web</a>
        </p></body></html>
        """
          .trimIndent(),
        baseHref = "OEBPS/xhtml/ch01.xhtml",
      )

    val hrefs =
      (result.document.blocks.single() as ParagraphBlock).spans.filterIsInstance<LinkSpan>().map {
        it.href
      }
    assertEquals(
      listOf(
        "OEBPS/text/ch02.xhtml#s3",
        "OEBPS/xhtml/ch01.xhtml#note1",
        "https://example.com/x",
      ),
      hrefs,
    )
  }

  @Test
  fun parse_anchorWrappingParagraphs_promotesBlocksWithoutInlineDiagnostics() {
    // TOC/nav pages commonly wrap paragraphs in anchors; those must stay separate paragraphs
    // instead of flattening into one line with a bogus unsupported-inline `p` count.
    val result =
      EpubParser.parse(
        """
        <html><body>
          <a href="ch01.xhtml"><p>Chapter One</p></a>
          <a href="ch02.xhtml"><p>Chapter Two</p></a>
        </body></html>
        """
          .trimIndent()
      )

    assertEquals(listOf("Chapter One", "Chapter Two"), result.document.paragraphText())
    assertTrue(result.diagnostics.unsupportedInlineTags.isEmpty())
  }

  @Test
  fun parse_anchorWithMixedInlineAndBlockContent_preservesAllText() {
    val result =
      EpubParser.parse(
        "<html><body><a href=\"ch01.xhtml\">Part One <p>Chapter One</p></a></body></html>"
      )

    assertEquals(
      listOf("Part One", "Chapter One"),
      result.document.paragraphText().map { it.trim() },
    )
  }

  @Test
  fun stylesheetHrefs_resolvesAgainstBaseHref() {
    assertEquals(
      listOf("OEBPS/styles/main.css"),
      EpubParser.stylesheetHrefs(
        "<html><head><link rel=\"stylesheet\" href=\"../styles/main.css\"/></head><body/></html>",
        baseHref = "OEBPS/xhtml/ch01.xhtml",
      ),
    )
  }
}
