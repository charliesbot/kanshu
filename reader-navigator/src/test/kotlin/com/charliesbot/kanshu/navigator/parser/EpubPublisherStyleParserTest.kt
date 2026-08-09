package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.BlockAlignment
import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.model.InlineStyle
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.TextLeaf
import com.charliesbot.kanshu.navigator.parser.css.CssParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubPublisherStyleParserTest {
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
  fun parse_withStylesheet_uncoveredBreakKeepsSingleBlockFallback() {
    val sheet = CssParser.parse("h2 span { display: block }")
    val result =
      EpubParser.parse(
        "<html><body><h2><span>CHAPTER 1</span><br><span>PLAY</span></h2></body></html>",
        stylesheets = listOf(sheet),
      )

    val heading = result.document.blocks.single() as HeadingBlock
    assertEquals("CHAPTER 1\nPLAY", heading.spans.joinToString("") { spanText(it) })
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
}
