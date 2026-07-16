package com.charliesbot.kanshu.navigator.parser.css

import com.helger.css.decl.CSSSelector
import com.helger.css.decl.CSSSelectorSimpleMember
import com.helger.css.decl.CascadingStyleSheet
import com.helger.css.decl.ECSSSelectorCombinator
import com.helger.css.reader.CSSReader
import com.helger.css.reader.CSSReaderSettings
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler

/**
 * Adapts ph-css (battle-tested, Apache 2.0) to the micro-cascade's [CssStylesheet] model.
 *
 * ph-css owns tokenization and grammar — the commodity layer, hardened by a decade of real-world
 * stylesheets. This adapter keeps Kanshu's decisions: which selector shapes the cascade honors
 * (type/class/id/compound/descendant), the property allowlist, and the census counts for everything
 * skipped. Unparseable input degrades to an empty stylesheet, never an exception —
 * semantic-tags-only rendering per docs/PRD_PUBLISHER_STYLES.md.
 */
object CssParser {
  fun parse(css: String): CssStylesheet {
    val sheet =
      runCatching { CSSReader.readFromStringReader(closeUnterminatedComment(css), READER_SETTINGS) }
        .getOrNull() ?: return CssStylesheet()
    return convert(sheet)
  }

  // CSS Syntax closes an unterminated comment at EOF; ph-css rejects it, so normalize first.
  // Note: a trailing rule whose *block* is unterminated at EOF is dropped by ph-css (spec would
  // keep it) — acceptable under the fewer-rules-never-throw rule, visible as a census anomaly.
  private fun closeUnterminatedComment(css: String): String {
    val lastOpen = css.lastIndexOf("/*")
    if (lastOpen == -1 || css.indexOf("*/", lastOpen + 2) != -1) return css
    return "$css*/"
  }

  private fun convert(sheet: CascadingStyleSheet): CssStylesheet {
    val rules = mutableListOf<CssRule>()
    val declarationCounts = linkedMapOf<String, Int>()
    var unsupportedSelectorCount = 0
    var importantCount = 0

    sheet.allStyleRules.forEach { styleRule ->
      val declarations = mutableListOf<CssDeclaration>()
      styleRule.allDeclarations.forEach { declaration ->
        val property = declaration.property.trim().lowercase()
        if (property.isEmpty()) return@forEach
        if (declaration.isImportant) importantCount++
        declarationCounts.merge(property, 1, Int::plus)
        if (property in ALLOWLISTED_PROPERTIES) {
          val value = declaration.expressionAsCSSString.trim().lowercase()
          if (value.isNotEmpty()) declarations.addAll(expandCssDeclaration(property, value))
        }
      }

      styleRule.allSelectors.forEach { selector ->
        val converted = convertSelector(selector)
        if (converted == null) {
          unsupportedSelectorCount++
        } else if (declarations.isNotEmpty()) {
          rules.add(CssRule(converted, declarations))
        }
      }
    }

    return CssStylesheet(
      rules = rules,
      stats =
        CssParseStats(
          declarationCounts = declarationCounts,
          unsupportedSelectorCount = unsupportedSelectorCount,
          atRuleCounts = atRuleCounts(sheet),
          importantCount = importantCount,
        ),
    )
  }

  /**
   * Maps a ph-css selector to the cascade's supported shapes: type, `.class`, `#id`, compounds,
   * descendant combinators. Anything else (child/sibling combinators, pseudos, attributes,
   * functions, `*`) returns null and is census-counted by the caller.
   */
  private fun convertSelector(selector: CSSSelector): CssSelector? {
    val compounds = mutableListOf<CssCompound>()
    var type: String? = null
    var id: String? = null
    var classes = mutableSetOf<String>()
    var compoundHasContent = false

    fun flushCompound(): Boolean {
      if (!compoundHasContent) return false
      compounds.add(CssCompound(type = type, id = id, classes = classes))
      type = null
      id = null
      classes = mutableSetOf()
      compoundHasContent = false
      return true
    }

    selector.allMembers.forEach { member ->
      when (member) {
        is ECSSSelectorCombinator ->
          when (member) {
            ECSSSelectorCombinator.BLANK -> if (!flushCompound()) return null
            else -> return null // child/sibling combinators unsupported
          }
        is CSSSelectorSimpleMember ->
          when {
            member.isClass -> {
              val name = member.value.removePrefix(".")
              // CSS-escaped identifiers can never match Jsoup's decoded classNames; count as
              // unsupported instead of retaining a dead rule.
              if ('\\' in name) return null
              classes.add(name)
              compoundHasContent = true
            }
            member.isHash -> {
              id = member.value.removePrefix("#")
              compoundHasContent = true
            }
            member.isElementName -> {
              val name = member.value.lowercase()
              if (name == "*" || type != null) return null
              type = name
              compoundHasContent = true
            }
            else -> return null // pseudo, nesting
          }
        else -> return null // attributes, :not(), functions
      }
    }
    if (!flushCompound()) return null
    return CssSelector(compounds)
  }

  private fun atRuleCounts(sheet: CascadingStyleSheet): Map<String, Int> {
    val counts = linkedMapOf<String, Int>()
    fun put(name: String, count: Int) {
      if (count > 0) counts[name] = count
    }
    put("@import", sheet.allImportRules.size)
    put("@namespace", sheet.allNamespaceRules.size)
    put("@media", sheet.allMediaRules.size)
    put("@font-face", sheet.allFontFaceRules.size)
    put("@keyframes", sheet.allKeyframesRules.size)
    put("@page", sheet.allPageRules.size)
    put("@viewport", sheet.allViewportRules.size)
    put("@supports", sheet.allSupportsRules.size)
    put("@unknown", sheet.allUnknownRules.size)
    return counts
  }

  // Browser-compliant mode mirrors how reading systems treat malformed publisher CSS: recover
  // where the spec allows, drop what cannot be parsed, never throw.
  private val READER_SETTINGS =
    CSSReaderSettings()
      .setBrowserCompliantMode(true)
      .setCustomErrorHandler(DoNothingCSSParseErrorHandler())
      .setCustomExceptionHandler { /* unrecoverable: readFromStringReader returns null */ }
}
