package com.charliesbot.kanshu.navigator.parser.css

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineCssDeclarationsTest {
  @Test
  fun parseInlineDeclarations_normalizesPropertiesAndSkipsMalformedSegments() {
    assertEquals(
      listOf(
        InlineCssDeclaration("font-style", "ITALIC"),
        InlineCssDeclaration("color", ""),
        InlineCssDeclaration("text-align", "center:right"),
      ),
      parseInlineDeclarations(
        " FONT-STYLE : ITALIC; malformed; color:; :none; text-align:center:right"
      ),
    )
  }
}
