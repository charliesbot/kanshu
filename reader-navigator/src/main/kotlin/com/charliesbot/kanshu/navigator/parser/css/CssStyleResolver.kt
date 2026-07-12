package com.charliesbot.kanshu.navigator.parser.css

import com.charliesbot.kanshu.navigator.model.BlockAlignment
import org.jsoup.nodes.Element

/**
 * Publisher signal resolved for one element after the cascade. `null` fields mean "no signal —
 * reader default." Values inherit down the DOM walk; an explicit declaration on a descendant
 * overrides the inherited value.
 */
internal data class ResolvedStyle(
  val italic: Boolean? = null,
  val bold: Boolean? = null,
  val textAlign: CssTextAlign? = null,
) {
  /**
   * Publisher block alignment for rendering. `Justify` maps to null — the reader's default already
   * justifies, so it is not a signal; `Start` survives as an explicit opt-out of justification
   * (poetry, code).
   */
  fun blockAlignment(): BlockAlignment? =
    when (textAlign) {
      CssTextAlign.Start -> BlockAlignment.Start
      CssTextAlign.Center -> BlockAlignment.Center
      CssTextAlign.End -> BlockAlignment.End
      CssTextAlign.Justify,
      null -> null
    }

  companion object {
    val None = ResolvedStyle()
  }
}

internal enum class CssTextAlign {
  Start,
  Center,
  End,
  Justify,
}

/**
 * The micro-cascade: matches an element against all stylesheet rules, applies winners in
 * (specificity, source order) order, then inline `style` on top, over the inherited style.
 *
 * Cascade semantics are honest (crengine's lesson); the property surface is tiny (Kindle's).
 */
internal class CssStyleResolver(stylesheets: List<CssStylesheet>) {
  // Pre-sorted by (specificity, source order); filter preserves order, so applying matches in
  // list order implements the cascade.
  private val rules: List<CssRule> =
    stylesheets
      .flatMap { it.rules }
      .withIndex()
      .sortedWith(compareBy({ it.value.selector.specificity }, { it.index }))
      .map { it.value }

  fun resolve(element: Element, inherited: ResolvedStyle): ResolvedStyle {
    var resolved = inherited
    rules
      .filter { it.selector.matches(element) }
      .forEach { rule -> resolved = resolved.applying(rule.declarations) }

    val inlineStyle = element.attr("style").trim()
    if (inlineStyle.isNotEmpty()) {
      resolved = resolved.applying(parseInlineDeclarations(inlineStyle))
    }
    return resolved
  }

  private fun parseInlineDeclarations(styleAttr: String): List<CssDeclaration> =
    styleAttr.split(';').mapNotNull { segment ->
      val property = segment.substringBefore(':', "").trim().lowercase()
      if (property.isEmpty() || !segment.contains(':') || property !in ALLOWLISTED_PROPERTIES) {
        return@mapNotNull null
      }
      val value =
        segment.substringAfter(':').removeSuffixIgnoreCase("!important").trim().lowercase()
      value.takeIf { it.isNotEmpty() }?.let { CssDeclaration(property, it) }
    }

  private fun String.removeSuffixIgnoreCase(suffix: String): String =
    if (trim().endsWith(suffix, ignoreCase = true)) trim().dropLast(suffix.length) else this
}

/**
 * Memoizing wrapper that folds inheritance down the ancestor chain: an element's resolved style
 * starts from its parent's resolved style. The DOM walk visits parents before children, so each
 * element resolves exactly once.
 */
internal class InheritedStyleResolver(private val resolver: CssStyleResolver) {
  private val cache = HashMap<Element, ResolvedStyle>()

  fun resolve(element: Element): ResolvedStyle {
    cache[element]?.let {
      return it
    }
    val parent = element.parent()
    val inherited =
      if (parent == null || parent.tagName() in NON_INHERITING_ROOTS) ResolvedStyle.None
      else resolve(parent)
    return resolver.resolve(element, inherited).also { cache[element] = it }
  }

  /**
   * Only the values declared by rules matching this element (or its inline style) — no inherited
   * values. Semantic tag emphasis may be reset by a declaration on the element itself, never by a
   * value merely inherited from an ancestor (mirrors how a UA `em` rule outranks inheritance).
   */
  fun resolveDeclared(element: Element): ResolvedStyle =
    resolver.resolve(element, ResolvedStyle.None)

  private companion object {
    val NON_INHERITING_ROOTS = setOf("#root", "html")
  }
}

private fun CssSelector.matches(element: Element): Boolean {
  if (!compounds.last().matches(element)) return false
  var compoundIndex = compounds.size - 2
  var ancestor = element.parent()
  while (compoundIndex >= 0 && ancestor != null) {
    if (compounds[compoundIndex].matches(ancestor)) {
      compoundIndex--
    }
    ancestor = ancestor.parent()
  }
  return compoundIndex < 0
}

private fun CssCompound.matches(element: Element): Boolean =
  (type == null || element.tagName().equals(type, ignoreCase = true)) &&
    (id == null || element.id() == id) &&
    element.classNames().containsAll(classes)

internal fun ResolvedStyle.applying(declarations: List<CssDeclaration>): ResolvedStyle {
  var resolved = this
  declarations.forEach { declaration ->
    resolved =
      when (declaration.property) {
        "font-style" ->
          when (declaration.value) {
            "italic",
            "oblique" -> resolved.copy(italic = true)
            "normal" -> resolved.copy(italic = false)
            else -> resolved
          }
        "font-weight" ->
          mapFontWeight(declaration.value)?.let { resolved.copy(bold = it) } ?: resolved
        "text-align" ->
          when (declaration.value) {
            "center" -> resolved.copy(textAlign = CssTextAlign.Center)
            "right",
            "end" -> resolved.copy(textAlign = CssTextAlign.End)
            "left",
            "start" -> resolved.copy(textAlign = CssTextAlign.Start)
            "justify" -> resolved.copy(textAlign = CssTextAlign.Justify)
            else -> resolved
          }
        else -> resolved
      }
  }
  return resolved
}

private fun mapFontWeight(value: String): Boolean? =
  when {
    value == "bold" || value == "bolder" -> true
    value == "normal" || value == "lighter" -> false
    else -> value.toIntOrNull()?.let { it >= 600 }
  }
