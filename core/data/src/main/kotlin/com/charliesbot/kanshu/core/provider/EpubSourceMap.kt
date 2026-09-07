package com.charliesbot.kanshu.core.provider

import com.charliesbot.kanshu.core.reader.SourceElementPath

/**
 * Reader-supplied EPUB structure and text lookup for translating provider anchors outside
 * rendering. Providers consume this contract when needed; they do not implement it.
 */
interface EpubSourceMap {
  /** Returns element metadata at a body-relative path, or null when that element is absent. */
  fun inspect(path: SourceElementPath): EpubSourceElement?

  /**
   * Resolves a zero-based element-child index, excluding text nodes and comments; null if absent.
   */
  fun resolveChild(parent: SourceElementPath, elementChildIndex: Int): SourceElementPath?

  /** Resolves an XHTML element ID to its body-relative path, or null if absent. */
  fun resolveElementId(id: String): SourceElementPath?

  /**
   * Finds selected text within the range covered by the start and end elements, including
   * descendants. Matching collapses whitespace and accounts for block boundaries. Returns an
   * inclusive range of original flattened-text offsets (use last + 1 as the exclusive end), or null
   * when the anchors or text cannot be resolved. Repeated text resolves to the first occurrence;
   * element anchors cannot distinguish occurrences.
   */
  fun findFirstLiteralMatch(
    startElementPath: SourceElementPath,
    endElementPath: SourceElementPath,
    selectedText: String,
  ): IntRange?
}

/**
 * Element metadata used to translate between source paths and provider-specific anchors.
 * [sameTagSiblingIndex] is zero-based among siblings with the same tag; [childPaths] are in DOM
 * order. [textRange] is an inclusive flattened-text range, or null when the element has no mapped
 * text.
 */
data class EpubSourceElement(
  val path: SourceElementPath,
  val tagName: String,
  val id: String?,
  val sameTagSiblingIndex: Int,
  val childPaths: List<SourceElementPath>,
  val textRange: IntRange?,
)
