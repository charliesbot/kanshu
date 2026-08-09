package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.StylingCensus
import com.charliesbot.kanshu.navigator.parser.css.CssParser
import org.junit.Assert.assertEquals
import org.junit.Test

class StylingCensusCollectorTest {
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
}
