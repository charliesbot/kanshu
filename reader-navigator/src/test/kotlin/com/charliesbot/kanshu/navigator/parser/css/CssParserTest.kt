package com.charliesbot.kanshu.navigator.parser.css

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CssParserTest {
  @Test
  fun parse_calibreStyleSheet_retainsAllowlistedRules() {
    val sheet =
      CssParser.parse(
        """
        .calibre7 { font-style: italic }
        p.center { text-align: center; margin: 0 }
        .bold, .strong { font-weight: bold }
        """
          .trimIndent()
      )

    assertEquals(4, sheet.rules.size)
    assertEquals(
      CssRule(
        selector = CssSelector(listOf(CssCompound(classes = setOf("calibre7")))),
        declarations = listOf(CssDeclaration("font-style", "italic")),
      ),
      sheet.rules[0],
    )
    assertEquals(
      CssSelector(listOf(CssCompound(type = "p", classes = setOf("center")))),
      sheet.rules[1].selector,
    )
    assertEquals(
      listOf(
        CssDeclaration("text-align", "center"),
        CssDeclaration("margin-top", "0"),
        CssDeclaration("margin-right", "0"),
        CssDeclaration("margin-bottom", "0"),
        CssDeclaration("margin-left", "0"),
      ),
      sheet.rules[1].declarations,
    )
    assertEquals(
      listOf(
        CssSelector(listOf(CssCompound(classes = setOf("bold")))),
        CssSelector(listOf(CssCompound(classes = setOf("strong")))),
      ),
      sheet.rules.drop(2).map { it.selector },
    )
  }

  @Test
  fun parse_descendantSelector_preservesCompoundChain() {
    val sheet = CssParser.parse("p.dedication span { font-style: italic }")

    assertEquals(
      CssSelector(
        listOf(
          CssCompound(type = "p", classes = setOf("dedication")),
          CssCompound(type = "span"),
        )
      ),
      sheet.rules.single().selector,
    )
  }

  @Test
  fun parse_specificity_ordersIdAboveClassAboveType() {
    val id = CssParser.parse("#x { font-style: italic }").rules.single().selector
    val classSelector = CssParser.parse(".x { font-style: italic }").rules.single().selector
    val type = CssParser.parse("p { font-style: italic }").rules.single().selector
    val compound = CssParser.parse("p.x.y { font-style: italic }").rules.single().selector

    assertTrue(id.specificity > classSelector.specificity)
    assertTrue(classSelector.specificity > type.specificity)
    assertTrue(compound.specificity > classSelector.specificity)
  }

  @Test
  fun parse_countsAllDeclarationsButRetainsOnlyAllowlisted() {
    val sheet =
      CssParser.parse(
        """
        .a { color: red; line-height: 1.2; font-style: italic }
        .b { float: left }
        """
          .trimIndent()
      )

    assertEquals(
      mapOf("color" to 1, "line-height" to 1, "font-style" to 1, "float" to 1),
      sheet.stats.declarationCounts,
    )
    assertEquals(1, sheet.rules.size)
    assertEquals(listOf(CssDeclaration("font-style", "italic")), sheet.rules.single().declarations)
  }

  @Test
  fun parse_marginShorthand_expandsPerCssValueCount() {
    val sheet =
      CssParser.parse(
        """
        .one { margin: 1em }
        .three { margin: 1em 2em 3em }
        """
          .trimIndent()
      )

    assertEquals(
      listOf(
        CssDeclaration("margin-top", "1em"),
        CssDeclaration("margin-right", "1em"),
        CssDeclaration("margin-bottom", "1em"),
        CssDeclaration("margin-left", "1em"),
      ),
      sheet.rules[0].declarations,
    )
    assertEquals(
      listOf(
        CssDeclaration("margin-top", "1em"),
        CssDeclaration("margin-right", "2em"),
        CssDeclaration("margin-bottom", "3em"),
        CssDeclaration("margin-left", "2em"),
      ),
      sheet.rules[1].declarations,
    )
  }

  @Test
  fun parse_unsupportedSelectors_areCountedAndSkippedWithoutLosingSiblings() {
    val sheet =
      CssParser.parse(
        """
        p > span, .kept { font-style: italic }
        a:hover { font-weight: bold }
        [epub|type="noteref"] { font-style: italic }
        """
          .trimIndent()
      )

    assertEquals(1, sheet.rules.size)
    assertEquals(
      CssSelector(listOf(CssCompound(classes = setOf("kept")))),
      sheet.rules.single().selector,
    )
    assertEquals(3, sheet.stats.unsupportedSelectorCount)
  }

  @Test
  fun parse_unsupportedSelectors_areCountedEvenWithoutAllowlistedDeclarations() {
    val sheet = CssParser.parse("a:hover { color: red }")

    assertEquals(1, sheet.stats.unsupportedSelectorCount)
    assertTrue(sheet.rules.isEmpty())
  }

  @Test
  fun parse_atRules_areSkippedStructurallyAndCounted() {
    val sheet =
      CssParser.parse(
        """
        @import url("other.css");
        @media (max-width: 600px) { .a { font-style: italic } }
        @font-face { font-family: "Publisher"; src: url("f.otf") }
        .b { font-style: italic }
        """
          .trimIndent()
      )

    assertEquals(1, sheet.rules.size)
    assertEquals(
      CssSelector(listOf(CssCompound(classes = setOf("b")))),
      sheet.rules.single().selector,
    )
    assertEquals(mapOf("@import" to 1, "@media" to 1, "@font-face" to 1), sheet.stats.atRuleCounts)
  }

  @Test
  fun parse_important_isCountedAndDeclarationKept() {
    val sheet = CssParser.parse(".a { font-style: italic !important }")

    assertEquals(1, sheet.stats.importantCount)
    assertEquals(listOf(CssDeclaration("font-style", "italic")), sheet.rules.single().declarations)
  }

  @Test
  fun parse_comments_areStripped() {
    val sheet = CssParser.parse("/* header */ .a { /* inline */ font-style: italic } /* trailing")

    assertEquals(listOf(CssDeclaration("font-style", "italic")), sheet.rules.single().declarations)
  }

  @Test
  fun parse_malformedInput_neverThrows() {
    listOf(
        "",
        "}}}}",
        ".a { font-style: italic",
        ".a {",
        ".a {/* truncated comment",
        "{ font-style: italic }",
        ".a font-style italic",
        "@media {",
        "..a { font-style: italic }",
      )
      .forEach { css -> CssParser.parse(css) }
  }
}
