package com.charliesbot.kanshu.navigator.parser.css

import com.charliesbot.kanshu.navigator.model.BlockAlignment
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CssStyleResolverTest {
  private fun resolverOf(vararg css: String): CssStyleResolver =
    CssStyleResolver(css.map(CssParser::parse))

  private fun element(html: String, selector: String): Element =
    checkNotNull(Jsoup.parse(html).selectFirst(selector))

  @Test
  fun resolve_classRule_appliesItalic() {
    val resolver = resolverOf(".calibre7 { font-style: italic }")
    val span = element("<p>a <span class=\"calibre7\">b</span></p>", "span")

    assertEquals(true, resolver.resolve(span, ResolvedStyle.None).italic)
  }

  @Test
  fun resolve_higherSpecificityWins() {
    val resolver = resolverOf("p.x { font-style: italic } .x { font-style: normal }")
    val paragraph = element("<p class=\"x\">a</p>", "p")

    assertEquals(true, resolver.resolve(paragraph, ResolvedStyle.None).italic)
  }

  @Test
  fun resolve_equalSpecificity_laterSourceOrderWins() {
    val resolver = resolverOf(".x { font-style: italic } .x { font-style: normal }")
    val paragraph = element("<p class=\"x\">a</p>", "p")

    assertEquals(false, resolver.resolve(paragraph, ResolvedStyle.None).italic)
  }

  @Test
  fun resolve_sourceOrderSpansStylesheets() {
    val resolver = resolverOf(".x { font-style: italic }", ".x { font-style: normal }")
    val paragraph = element("<p class=\"x\">a</p>", "p")

    assertEquals(false, resolver.resolve(paragraph, ResolvedStyle.None).italic)
  }

  @Test
  fun resolve_descendantSelector_matchesOnlyInsideAncestor() {
    val resolver = resolverOf("p.dedication span { font-style: italic }")
    val html =
      "<div><p class=\"dedication\"><span id=\"in\">a</span></p><span id=\"out\">b</span></div>"

    assertEquals(true, resolver.resolve(element(html, "#in"), ResolvedStyle.None).italic)
    assertNull(resolver.resolve(element(html, "#out"), ResolvedStyle.None).italic)
  }

  @Test
  fun resolve_inlineStyle_overridesStylesheets() {
    val resolver = resolverOf(".x { text-align: center }")
    val paragraph = element("<p class=\"x\" style=\"text-align: right\">a</p>", "p")

    assertEquals(CssTextAlign.End, resolver.resolve(paragraph, ResolvedStyle.None).textAlign)
  }

  @Test
  fun resolve_inheritedValueSurvivesUnlessOverridden() {
    val resolver = resolverOf("span.normal { font-style: normal }")
    val inherited = ResolvedStyle(italic = true)
    val html = "<p><span id=\"plain\">a</span><span id=\"reset\" class=\"normal\">b</span></p>"

    assertEquals(true, resolver.resolve(element(html, "#plain"), inherited).italic)
    assertEquals(false, resolver.resolve(element(html, "#reset"), inherited).italic)
  }

  @Test
  fun resolve_numericFontWeights_mapAtSixHundred() {
    val resolver = resolverOf(".w7 { font-weight: 700 } .w4 { font-weight: 400 }")
    val html = "<p><span class=\"w7\" id=\"a\">a</span><span class=\"w4\" id=\"b\">b</span></p>"

    assertEquals(true, resolver.resolve(element(html, "#a"), ResolvedStyle.None).bold)
    assertEquals(false, resolver.resolve(element(html, "#b"), ResolvedStyle.None).bold)
  }

  @Test
  fun blockAlignment_mapsJustifyToNoSignalAndKeepsExplicitStart() {
    assertEquals(
      BlockAlignment.Center,
      ResolvedStyle(textAlign = CssTextAlign.Center).blockAlignment(),
    )
    assertEquals(
      BlockAlignment.Start,
      ResolvedStyle(textAlign = CssTextAlign.Start).blockAlignment(),
    )
    assertNull(ResolvedStyle(textAlign = CssTextAlign.Justify).blockAlignment())
    assertNull(ResolvedStyle.None.blockAlignment())
  }

  @Test
  fun resolve_idSelector_beatsClassSelector() {
    val resolver = resolverOf("#ded { font-style: normal } .x { font-style: italic }")
    val paragraph = element("<p id=\"ded\" class=\"x\">a</p>", "p")

    assertEquals(false, resolver.resolve(paragraph, ResolvedStyle.None).italic)
  }

  @Test
  fun resolve_spacingLengths_normalizeToEm() {
    val resolver =
      resolverOf(".s { margin-top: 1em; margin-bottom: 12pt; margin-left: 32px; text-indent: 5% }")
    val style = resolver.resolve(element("<p class=\"s\">a</p>", "p"), ResolvedStyle.None)

    assertEquals(1f, checkNotNull(style.marginTopEm), 0.001f)
    assertEquals(1f, checkNotNull(style.marginBottomEm), 0.001f)
    assertEquals(2f, checkNotNull(style.marginStartEm), 0.001f)
    // Percentages are width-relative, not font-relative — treated as no signal.
    assertNull(style.textIndentEm)
  }

  @Test
  fun resolve_spacingValues_clampAndRejectGarbage() {
    val resolver =
      resolverOf(
        ".s { margin-top: 40em; margin-bottom: -1em; text-indent: 99em; margin-left: auto }"
      )
    val style = resolver.resolve(element("<p class=\"s\">a</p>", "p"), ResolvedStyle.None)

    assertEquals(2f, checkNotNull(style.marginTopEm), 0.001f)
    assertNull(style.marginBottomEm) // negative -> no signal
    assertEquals(3f, checkNotNull(style.textIndentEm), 0.001f)
    assertNull(style.marginStartEm) // auto -> no signal
  }

  @Test
  fun resolve_marginShorthand_expandsToVerticalAndHorizontalComponents() {
    val resolver = resolverOf(".s { margin: 1em 0 }")
    val style = resolver.resolve(element("<p class=\"s\">a</p>", "p"), ResolvedStyle.None)

    assertEquals(1f, checkNotNull(style.marginTopEm), 0.001f)
    assertEquals(1f, checkNotNull(style.marginBottomEm), 0.001f)
    assertEquals(0f, checkNotNull(style.marginStartEm), 0.001f)
    assertEquals(0f, checkNotNull(style.marginEndEm), 0.001f)
  }

  @Test
  fun inheritedResolver_marginsDoNotInherit_textIndentDoes() {
    val inherited =
      InheritedStyleResolver(resolverOf("div.wrap { margin-top: 1em; text-indent: 2em }"))
    val html = "<div class=\"wrap\"><p id=\"child\">a</p></div>"
    val style = inherited.resolve(element(html, "#child"))

    assertNull(style.marginTopEm)
    assertEquals(2f, checkNotNull(style.textIndentEm), 0.001f)
  }

  @Test
  fun blockSpacing_isNullWhenNothingDeclared() {
    assertNull(ResolvedStyle.None.blockSpacing())
    assertEquals(0f, checkNotNull(ResolvedStyle(textIndentEm = 0f).blockSpacing()?.textIndentEm))
  }
}
