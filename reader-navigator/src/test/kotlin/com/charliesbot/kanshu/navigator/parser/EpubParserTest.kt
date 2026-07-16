package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.BlockAlignment
import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.model.HorizontalRule
import com.charliesbot.kanshu.navigator.model.ImageBlock
import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.LinkSpan
import com.charliesbot.kanshu.navigator.model.ListBlock
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.QuoteBlock
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.model.StyledGroup
import com.charliesbot.kanshu.navigator.model.StylingCensus
import com.charliesbot.kanshu.navigator.model.TextLeaf
import com.charliesbot.kanshu.navigator.model.TextSpan
import com.charliesbot.kanshu.navigator.parser.css.CssParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun ReaderDocument.paragraphText(): List<String> =
  blocks.filterIsInstance<ParagraphBlock>().map { block ->
    block.spans.joinToString("") { spanText(it) }
  }

internal fun spanText(span: TextSpan): String =
  when (span) {
    is TextLeaf -> span.text
    is LinkSpan -> span.children.joinToString("") { spanText(it) }
    is StyledGroup -> span.children.joinToString("") { spanText(it) }
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
  fun parse_inlineImage_preservesAltText() {
    val result =
      EpubParser.parse(
        "<html><body><p>Before <img alt=\"ornament\" src=\"ornament.png\"/> after.</p></body></html>"
      )

    assertEquals(listOf("Before ornament after."), result.document.paragraphText())
    assertEquals(1, result.diagnostics.unsupportedInlineTags["img"])
  }

  @Test
  fun parse_blockImage_preservesImageBlockWithoutDiagnostics() {
    val result =
      EpubParser.parse(
        "<html><body><img alt=\"Map of the route\" src=\"images/map.png\"/></body></html>"
      )

    assertEquals(
      listOf(ImageBlock(resourceHref = "images/map.png", alt = "Map of the route")),
      result.document.blocks,
    )
    assertTrue(result.diagnostics.unsupportedBlockTags.isEmpty())
    assertTrue(result.diagnostics.unsupportedInlineTags.isEmpty())
  }

  @Test
  fun parse_imageOnlyParagraph_preservesImageBlockWithoutInlineFallback() {
    val result =
      EpubParser.parse(
        "<html><body><p><img alt=\"Cover image\" src=\"images/cover.jpg\"/></p></body></html>"
      )

    assertEquals(
      listOf(ImageBlock(resourceHref = "images/cover.jpg", alt = "Cover image")),
      result.document.blocks,
    )
    assertTrue(result.diagnostics.unsupportedBlockTags.isEmpty())
    assertTrue(result.diagnostics.unsupportedInlineTags.isEmpty())
  }

  @Test
  fun parse_imageOnlyDiv_preservesImageBlockWithoutInlineFallback() {
    val result =
      EpubParser.parse(
        "<html><body><div><img alt=\"Cover image\" src=\"images/cover.jpg\"/></div></body></html>"
      )

    assertEquals(
      listOf(ImageBlock(resourceHref = "images/cover.jpg", alt = "Cover image")),
      result.document.blocks,
    )
    assertTrue(result.diagnostics.unsupportedBlockTags.isEmpty())
    assertTrue(result.diagnostics.unsupportedInlineTags.isEmpty())
  }

  @Test
  fun parse_imageOnlyParagraphWithoutSrc_preservesImageBlockWithoutInlineFallback() {
    val result = EpubParser.parse("<html><body><p><img alt=\"Cover image\"/></p></body></html>")

    assertEquals(listOf(ImageBlock(resourceHref = "", alt = "Cover image")), result.document.blocks)
    assertTrue(result.diagnostics.unsupportedBlockTags.isEmpty())
    assertTrue(result.diagnostics.unsupportedInlineTags.isEmpty())
  }

  @Test
  fun parse_imageOnlyDivWithoutSrc_preservesImageBlockWithoutInlineFallback() {
    val result = EpubParser.parse("<html><body><div><img/></div></body></html>")

    assertEquals(listOf(ImageBlock(resourceHref = "", alt = null)), result.document.blocks)
    assertTrue(result.diagnostics.unsupportedBlockTags.isEmpty())
    assertTrue(result.diagnostics.unsupportedInlineTags.isEmpty())
  }

  @Test
  fun parse_imageWithBaseHref_resolvesSiblingRelativeSrc() {
    val result =
      EpubParser.parse(
        "<html><body><img alt=\"Map\" src=\"images/map.png\"/></body></html>",
        baseHref = "OEBPS/xhtml/chapter01.xhtml",
      )

    assertEquals(
      listOf(ImageBlock(resourceHref = "OEBPS/xhtml/images/map.png", alt = "Map")),
      result.document.blocks,
    )
  }

  @Test
  fun parse_imageWithBaseHref_resolvesParentRelativeSrc() {
    val result =
      EpubParser.parse(
        "<html><body><img alt=\"Map\" src=\"../images/map.png\"/></body></html>",
        baseHref = "OEBPS/xhtml/chapter01.xhtml",
      )

    assertEquals(
      listOf(ImageBlock(resourceHref = "OEBPS/images/map.png", alt = "Map")),
      result.document.blocks,
    )
  }

  @Test
  fun parse_imageWithBaseHref_treatsRootRelativeSrcAsPublicationRoot() {
    val result =
      EpubParser.parse(
        "<html><body><img alt=\"Map\" src=\"/images/map.png\"/></body></html>",
        baseHref = "OEBPS/xhtml/chapter01.xhtml",
      )

    assertEquals(
      listOf(ImageBlock(resourceHref = "images/map.png", alt = "Map")),
      result.document.blocks,
    )
  }

  @Test
  fun parse_imageWithBaseHref_keepsAbsoluteAndDataSrcUntouched() {
    val result =
      EpubParser.parse(
        """
        <html><body>
          <img alt="Remote" src="https://example.com/map.png"/>
          <img alt="Inline" src="data:image/png;base64,AAAA"/>
        </body></html>
        """
          .trimIndent(),
        baseHref = "OEBPS/xhtml/chapter01.xhtml",
      )

    assertEquals(
      listOf(
        ImageBlock(resourceHref = "https://example.com/map.png", alt = "Remote"),
        ImageBlock(resourceHref = "data:image/png;base64,AAAA", alt = "Inline"),
      ),
      result.document.blocks,
    )
  }

  @Test
  fun parse_imageWithBaseHref_clampsParentTraversalAboveRoot() {
    val result =
      EpubParser.parse(
        "<html><body><img alt=\"Map\" src=\"../../../images/map.png\"/></body></html>",
        baseHref = "OEBPS/chapter01.xhtml",
      )

    assertEquals(
      listOf(ImageBlock(resourceHref = "images/map.png", alt = "Map")),
      result.document.blocks,
    )
  }

  @Test
  fun parse_imageWithoutBaseHref_keepsSrcUnchanged() {
    val result =
      EpubParser.parse("<html><body><img alt=\"Map\" src=\"../images/map.png\"/></body></html>")

    assertEquals(
      listOf(ImageBlock(resourceHref = "../images/map.png", alt = "Map")),
      result.document.blocks,
    )
  }

  @Test
  fun parse_imageWithBaseHref_stripsFragmentAndQueryFromSrc() {
    val result =
      EpubParser.parse(
        """
        <html><body>
          <img alt="Fragment" src="images/map.png#section"/>
          <img alt="Query" src="images/chart.png?v=2"/>
        </body></html>
        """
          .trimIndent(),
        baseHref = "OEBPS/xhtml/chapter01.xhtml",
      )

    assertEquals(
      listOf(
        ImageBlock(resourceHref = "OEBPS/xhtml/images/map.png", alt = "Fragment"),
        ImageBlock(resourceHref = "OEBPS/xhtml/images/chart.png", alt = "Query"),
      ),
      result.document.blocks,
    )
  }

  @Test
  fun parse_withStylesheet_appliesClassEmphasisLikeSemanticTags() {
    val sheet = CssParser.parse(".calibre7 { font-style: italic } .b7 { font-weight: 700 }")
    val result =
      EpubParser.parse(
        "<html><body><p>It was <span class=\"calibre7\">not</span> a " +
          "<span class=\"b7\">good</span> idea.</p></body></html>",
        stylesheets = listOf(sheet),
      )

    val block = result.document.blocks.single() as ParagraphBlock
    assertEquals(
      listOf(
        TextLeaf("It was "),
        TextLeaf("not", InlineStyle.Italic),
        TextLeaf(" a "),
        TextLeaf("good", InlineStyle.Bold),
        TextLeaf(" idea."),
      ),
      block.spans,
    )
  }

  @Test
  fun parse_withStylesheet_appliesBlockAlignment() {
    val sheet = CssParser.parse("p.center { text-align: center } h1 { text-align: center }")
    val result =
      EpubParser.parse(
        """
        <html><body>
          <h1>Chapter One</h1>
          <p class="center">* * *</p>
          <p>Plain paragraph.</p>
        </body></html>
        """
          .trimIndent(),
        stylesheets = listOf(sheet),
      )

    assertEquals(
      BlockAlignment.Center,
      (result.document.blocks[0] as HeadingBlock).alignment,
    )
    assertEquals(
      BlockAlignment.Center,
      (result.document.blocks[1] as ParagraphBlock).alignment,
    )
    assertNull((result.document.blocks[2] as ParagraphBlock).alignment)
  }

  @Test
  fun parse_withStylesheet_appliesStructuralSpacing() {
    // Shaped like the Hachette InDesign export that motivated the slice: CRTS = spaced copyright
    // paragraph, CRT = glued continuation, TX = indented body text, COTX = unindented opener.
    val sheet =
      CssParser.parse(
        """
        p.CRTS { margin: 1em 0 0 0; text-indent: 0 }
        p.CRT { margin: 0; text-indent: 0 }
        p.TX { margin: 0; text-indent: 18pt }
        p.COTX { margin: 0; text-indent: 0 }
        """
          .trimIndent()
      )
    val result =
      EpubParser.parse(
        """
        <html><body>
          <p class="CRTS">PublicAffairs</p>
          <p class="CRT">Hachette Book Group</p>
          <p class="COTX">A friend of a friend is suddenly posting.</p>
          <p class="TX">She questions the accuracy of PCR tests.</p>
          <p>Unstyled paragraph.</p>
        </body></html>
        """
          .trimIndent(),
        stylesheets = listOf(sheet),
      )

    val blocks = result.document.blocks.filterIsInstance<ParagraphBlock>()
    val spaced = checkNotNull(blocks[0].spacing)
    assertEquals(1f, checkNotNull(spaced.marginTopEm), 0.001f)
    assertEquals(0f, checkNotNull(spaced.marginBottomEm), 0.001f)
    assertEquals(0f, checkNotNull(spaced.textIndentEm), 0.001f)

    val glued = checkNotNull(blocks[1].spacing)
    assertEquals(0f, checkNotNull(glued.marginTopEm), 0.001f)
    assertEquals(0f, checkNotNull(glued.marginBottomEm), 0.001f)

    assertEquals(0f, checkNotNull(checkNotNull(blocks[2].spacing).textIndentEm), 0.001f)
    assertEquals(1.5f, checkNotNull(checkNotNull(blocks[3].spacing).textIndentEm), 0.001f)

    // No matching rules -> no publisher spacing; the renderer applies the indent convention.
    assertNull(blocks[4].spacing)
  }

  @Test
  fun parse_inlineStyleAttribute_appliesWithoutStylesheets() {
    val result =
      EpubParser.parse(
        "<html><body><p style=\"text-align: center\">A <span style=\"font-style: italic\">b</span></p></body></html>"
      )

    val block = result.document.blocks.single() as ParagraphBlock
    assertEquals(BlockAlignment.Center, block.alignment)
    assertEquals(listOf(TextLeaf("A "), TextLeaf("b", InlineStyle.Italic)), block.spans)
  }

  @Test
  fun parse_withStylesheet_emphasisInheritsFromContainerToParagraphText() {
    val sheet = CssParser.parse("div.foreword { font-style: italic }")
    val result =
      EpubParser.parse(
        "<html><body><div class=\"foreword\"><p>Inherited text.</p></div></body></html>",
        stylesheets = listOf(sheet),
      )

    val block = result.document.blocks.single() as ParagraphBlock
    assertEquals(listOf(TextLeaf("Inherited text.", InlineStyle.Italic)), block.spans)
  }

  @Test
  fun parse_withStylesheet_semanticEmphasisSurvivesInheritedNormal() {
    // InDesign-style body classes reset font-style/weight on every paragraph; a nested semantic
    // <em>/<strong> must still win — only a rule matched on the element itself may reset it.
    val sheet = CssParser.parse("p.body { font-style: normal; font-weight: normal }")
    val result =
      EpubParser.parse(
        "<html><body><p class=\"body\">a <em>x</em> and <strong>y</strong></p></body></html>",
        stylesheets = listOf(sheet),
      )

    val block = result.document.blocks.single() as ParagraphBlock
    assertEquals(
      listOf(
        TextLeaf("a "),
        TextLeaf("x", InlineStyle.Italic),
        TextLeaf(" and "),
        TextLeaf("y", InlineStyle.Bold),
      ),
      block.spans,
    )
  }

  @Test
  fun parse_withStylesheet_semanticTagInsideMatchingCssContextStaysUnchanged() {
    // <em> inside an already-italic context must stay Italic, not gain spurious bold; same for
    // <strong> inside a bold context.
    val sheet = CssParser.parse(".foreword { font-style: italic } .heavy { font-weight: bold }")
    val result =
      EpubParser.parse(
        """
        <html><body>
          <div class="foreword"><p>a <em>x</em></p></div>
          <div class="heavy"><p>b <strong>y</strong></p></div>
        </body></html>
        """
          .trimIndent(),
        stylesheets = listOf(sheet),
      )

    assertEquals(
      listOf(TextLeaf("a ", InlineStyle.Italic), TextLeaf("x", InlineStyle.Italic)),
      (result.document.blocks[0] as ParagraphBlock).spans,
    )
    assertEquals(
      listOf(TextLeaf("b ", InlineStyle.Bold), TextLeaf("y", InlineStyle.Bold)),
      (result.document.blocks[1] as ParagraphBlock).spans,
    )
  }

  @Test
  fun parse_withStylesheet_cssNormalResetsInheritedEmphasis() {
    val sheet = CssParser.parse(".it { font-style: italic } .plain { font-style: normal }")
    val result =
      EpubParser.parse(
        "<html><body><p class=\"it\">a <span class=\"plain\">b</span></p></body></html>",
        stylesheets = listOf(sheet),
      )

    val block = result.document.blocks.single() as ParagraphBlock
    assertEquals(listOf(TextLeaf("a ", InlineStyle.Italic), TextLeaf("b")), block.spans)
  }

  @Test
  fun parse_withStylesheet_censusAggregatesStylesheetStats() {
    val sheet =
      CssParser.parse(
        """
        @import url("other.css");
        .a { font-style: italic; margin: 0 }
        p > span { font-weight: bold }
        .b { text-align: center !important }
        """
          .trimIndent()
      )
    val result =
      EpubParser.parse(
        "<html><body><p class=\"a\">x</p></body></html>",
        stylesheets = listOf(sheet),
      )

    val census = result.diagnostics.stylingCensus
    assertEquals(
      mapOf("font-style" to 1, "margin" to 1, "font-weight" to 1, "text-align" to 1),
      census.stylesheetPropertyCounts,
    )
    assertEquals(1, census.unsupportedSelectorCount)
    assertEquals(mapOf("@import" to 1), census.atRuleCounts)
    assertEquals(1, census.importantCount)
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

  @Test
  fun parse_stylingCensus_countsClassAndStyleAttributes() {
    val result =
      EpubParser.parse(
        """
        <html><body>
          <p class="calibre1">One</p>
          <p class="calibre1 center">Two</p>
          <p style="font-style: italic; text-align: center">Three</p>
          <span class="calibre7">Four</span>
        </body></html>
        """
          .trimIndent()
      )

    val census = result.diagnostics.stylingCensus
    assertEquals(3, census.classAttributeCount)
    assertEquals(1, census.styleAttributeCount)
    assertEquals(mapOf("calibre1" to 2, "center" to 1, "calibre7" to 1), census.classNameCounts)
    assertEquals(mapOf("font-style" to 1, "text-align" to 1), census.inlinePropertyCounts)
  }

  @Test
  fun parse_stylingCensus_collectsStylesheetHrefsResolvedAgainstBase() {
    val result =
      EpubParser.parse(
        """
        <html>
          <head>
            <link rel="stylesheet" type="text/css" href="../styles/stylesheet.css"/>
            <link rel="stylesheet" href="page.css"/>
            <style>p { font-style: italic; }</style>
          </head>
          <body><p>Text</p></body>
        </html>
        """
          .trimIndent(),
        baseHref = "OEBPS/xhtml/chapter01.xhtml",
      )

    val census = result.diagnostics.stylingCensus
    assertEquals(
      listOf("OEBPS/styles/stylesheet.css", "OEBPS/xhtml/page.css"),
      census.stylesheetHrefs,
    )
    assertEquals(1, census.styleTagCount)
  }

  @Test
  fun parse_stylingCensus_isEmptyForUnstyledMarkup() {
    val result = EpubParser.parse("<html><body><p>Plain</p></body></html>")

    assertEquals(StylingCensus(), result.diagnostics.stylingCensus)
  }

  @Test
  fun parse_imageWithBaseHrefAtRoot_resolvesAgainstRoot() {
    val result =
      EpubParser.parse(
        "<html><body><img alt=\"Map\" src=\"images/map.png\"/></body></html>",
        baseHref = "chapter01.xhtml",
      )

    assertEquals(
      listOf(ImageBlock(resourceHref = "images/map.png", alt = "Map")),
      result.document.blocks,
    )
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
