package com.charliesbot.kanshu.navigator.parser.css

import com.charliesbot.kanshu.navigator.model.BlockAlignment
import com.charliesbot.kanshu.navigator.model.BlockSpacing
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
  // Structural spacing, normalized to em and clamped at application time (see
  // docs/PRD_PUBLISHER_STYLES.md § Structural Spacing). Margins do NOT inherit per CSS —
  // InheritedStyleResolver strips them before passing a parent style down; text-indent inherits.
  val marginTopEm: Float? = null,
  val marginBottomEm: Float? = null,
  val marginStartEm: Float? = null,
  val marginEndEm: Float? = null,
  val textIndentEm: Float? = null,
) {
  /** The subset that flows to children during the DOM walk — margins are non-inheriting. */
  fun inheritable(): ResolvedStyle =
    copy(marginTopEm = null, marginBottomEm = null, marginStartEm = null, marginEndEm = null)

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

  /** Publisher structural spacing for the block model, or null when nothing was declared. */
  fun blockSpacing(): BlockSpacing? =
    BlockSpacing(
        marginTopEm = marginTopEm,
        marginBottomEm = marginBottomEm,
        marginStartEm = marginStartEm,
        marginEndEm = marginEndEm,
        textIndentEm = textIndentEm,
      )
      .takeUnless { it == BlockSpacing() }

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
    styleAttr.split(';').flatMap { segment ->
      val property = segment.substringBefore(':', "").trim().lowercase()
      if (property.isEmpty() || !segment.contains(':') || property !in ALLOWLISTED_PROPERTIES) {
        return@flatMap emptyList()
      }
      val value =
        segment.substringAfter(':').removeSuffixIgnoreCase("!important").trim().lowercase()
      if (value.isEmpty()) emptyList() else expandCssDeclaration(property, value)
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
      else resolve(parent).inheritable()
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
        "margin-top" ->
          resolved.applyingLength(declaration.value, MAX_VERTICAL_MARGIN_EM) {
            copy(marginTopEm = it)
          }
        "margin-bottom" ->
          resolved.applyingLength(declaration.value, MAX_VERTICAL_MARGIN_EM) {
            copy(marginBottomEm = it)
          }
        "margin-left" ->
          resolved.applyingLength(declaration.value, MAX_HORIZONTAL_INSET_EM) {
            copy(marginStartEm = it)
          }
        "margin-right" ->
          resolved.applyingLength(declaration.value, MAX_HORIZONTAL_INSET_EM) {
            copy(marginEndEm = it)
          }
        "text-indent" ->
          resolved.applyingLength(declaration.value, MAX_TEXT_INDENT_EM) {
            copy(textIndentEm = it)
          }
        else -> resolved
      }
  }
  return resolved
}

/**
 * Parses [value] as a CSS length, clamps it to [maxEm], and applies it via [set]; no-signal values
 * leave the style unchanged.
 */
private fun ResolvedStyle.applyingLength(
  value: String,
  maxEm: Float,
  set: ResolvedStyle.(Float) -> ResolvedStyle,
): ResolvedStyle = parseCssLengthToEm(value)?.let { set(it.coerceAtMost(maxEm)) } ?: this

// Normalization clamps from docs/PRD_PUBLISHER_STYLES.md § Structural Spacing. Lengths are
// already non-negative (parseCssLengthToEm treats negatives as no signal).
private const val MAX_VERTICAL_MARGIN_EM = 2f
private const val MAX_TEXT_INDENT_EM = 3f
private const val MAX_HORIZONTAL_INSET_EM = 6f

private fun mapFontWeight(value: String): Boolean? =
  when {
    value == "bold" || value == "bolder" -> true
    value == "normal" || value == "lighter" -> false
    else -> value.toIntOrNull()?.let { it >= 600 }
  }
