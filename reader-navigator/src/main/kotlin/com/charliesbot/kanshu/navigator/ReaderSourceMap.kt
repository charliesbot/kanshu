package com.charliesbot.kanshu.navigator

import com.charliesbot.kanshu.core.reader.SourceElementPath
import com.charliesbot.kanshu.navigator.engine.SpanFlattener
import com.charliesbot.kanshu.navigator.model.ReaderBlock

data class ReaderSourceElement(
  val path: SourceElementPath,
  val tagName: String,
  val id: String?,
  val sameTagSiblingIndex: Int,
  val childPaths: List<SourceElementPath>,
  val textRange: IntRange?,
)

/**
 * Immutable relationship between XHTML elements and the exact flattened reader text stream.
 * Provider adapters use this boundary; rendering continues to use character offsets directly.
 */
class ReaderSourceMap
internal constructor(
  private val text: String,
  private val elements: Map<SourceElementPath, ElementRecord>,
  private val characterPaths: List<SourceElementPath?>,
) {
  fun pathAt(charOffset: Int): SourceElementPath? = characterPaths.getOrNull(charOffset)

  fun inspect(path: SourceElementPath): ReaderSourceElement? {
    val element = elements[path] ?: return null
    return ReaderSourceElement(
      path = path,
      tagName = element.tagName,
      id = element.id,
      sameTagSiblingIndex = element.sameTagSiblingIndex,
      childPaths = element.childPaths,
      textRange = rangeFor(path),
    )
  }

  fun resolveChild(parent: SourceElementPath, elementChildIndex: Int): SourceElementPath? =
    elements[parent]?.childPaths?.getOrNull(elementChildIndex)

  fun resolveElementId(id: String): SourceElementPath? =
    elements.values.firstOrNull { it.id == id }?.path

  /**
   * Finds the first literal match after whitespace collapse and returns its original half-open
   * stream range. Search is bounded by the addressed source elements.
   */
  fun findFirstLiteralMatch(
    startElementPath: SourceElementPath,
    endElementPath: SourceElementPath,
    selectedText: String,
  ): IntRange? {
    val start = rangeFor(startElementPath)?.first ?: return null
    val end = rangeFor(endElementPath)?.last ?: return null
    if (end < start) return null
    val normalizedNeedle = normalizeWhitespace(selectedText)
    if (normalizedNeedle.isEmpty()) return null
    val (normalizedHaystack, offsets) = normalizeWhitespaceWithOffsets(text, start, end + 1)
    val index = normalizedHaystack.indexOf(normalizedNeedle)
    if (index < 0) return null
    val originalStart = offsets[index]
    val originalEnd = offsets[index + normalizedNeedle.lastIndex] + 1
    return originalStart until originalEnd
  }

  private fun rangeFor(path: SourceElementPath): IntRange? {
    var first = Int.MAX_VALUE
    var last = -1
    characterPaths.forEachIndexed { index, owner ->
      if (owner != null && owner.isDescendantOf(path)) {
        first = minOf(first, index)
        last = index
      }
    }
    return if (last < 0) null else first..last
  }

  private fun SourceElementPath.isDescendantOf(ancestor: SourceElementPath): Boolean =
    childIndexes.size >= ancestor.childIndexes.size &&
      childIndexes.take(ancestor.childIndexes.size) == ancestor.childIndexes

  internal data class ElementRecord(
    val path: SourceElementPath,
    val tagName: String,
    val id: String?,
    val sameTagSiblingIndex: Int,
    val childPaths: List<SourceElementPath>,
  )

  internal companion object {
    val Empty = ReaderSourceMap("", emptyMap(), emptyList())

    fun create(blocks: List<ReaderBlock>, elements: List<ElementRecord>): ReaderSourceMap {
      val flattened = SpanFlattener.sourceStream(blocks)
      return ReaderSourceMap(
        text = flattened.text,
        elements = elements.associateBy { it.path },
        characterPaths = flattened.sourcePaths,
      )
    }
  }
}

fun normalizeWhitespace(value: String): String = value.trim().replace(Regex("\\s+"), " ")

private fun normalizeWhitespaceWithOffsets(
  value: String,
  start: Int,
  endExclusive: Int,
): Pair<String, List<Int>> {
  val normalized = StringBuilder()
  val offsets = mutableListOf<Int>()
  var inWhitespace = false
  for (index in start until endExclusive.coerceAtMost(value.length)) {
    val char = value[index]
    if (char.isWhitespace()) {
      if (normalized.isNotEmpty() && !inWhitespace) {
        normalized.append(' ')
        offsets += index
      }
      inWhitespace = true
    } else {
      normalized.append(char)
      offsets += index
      inWhitespace = false
    }
  }
  if (normalized.lastOrNull() == ' ') {
    normalized.deleteCharAt(normalized.lastIndex)
    offsets.removeAt(offsets.lastIndex)
  }
  return normalized.toString() to offsets
}
