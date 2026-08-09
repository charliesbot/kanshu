package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.ImageBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubImageParserTest {
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
}
