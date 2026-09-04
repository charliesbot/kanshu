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
  private val searchBoundaries: Set<Int>,
  private val textRanges: Map<SourceElementPath, IntRange>,
) {
  fun pathAt(charOffset: Int): SourceElementPath? = characterPaths.getOrNull(charOffset)

  internal fun resolveSelectionPaths(
    startCharOffset: Int,
    endCharOffset: Int,
  ): Pair<SourceElementPath, SourceElementPath> {
    val selection = startCharOffset until endCharOffset
    val startPath =
      pathAt(startCharOffset) ?: selection.firstNotNullOfOrNull(::pathAt) ?: SourceElementPath.Root
    val endPath =
      pathAt(endCharOffset - 1)
        ?: selection.reversed().firstNotNullOfOrNull(::pathAt)
        ?: SourceElementPath.Root
    return startPath to endPath
  }

  fun inspect(path: SourceElementPath): ReaderSourceElement? {
    val element = elements[path] ?: return null
    return ReaderSourceElement(
      path = path,
      tagName = element.tagName,
      id = element.id,
      sameTagSiblingIndex = element.sameTagSiblingIndex,
      childPaths = List(element.childCount, path::child),
      textRange = textRanges[path],
    )
  }

  fun resolveChild(parent: SourceElementPath, elementChildIndex: Int): SourceElementPath? {
    val element = elements[parent] ?: return null
    if (elementChildIndex !in 0 until element.childCount) return null
    return parent.child(elementChildIndex)
  }

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
    val start = textRanges[startElementPath]?.first ?: return null
    val end = textRanges[endElementPath]?.last ?: return null
    if (end < start) return null
    val normalizedNeedle = normalizeWhitespace(selectedText)
    if (normalizedNeedle.isEmpty()) return null
    val (normalizedHaystack, offsets) =
      normalizeWhitespaceWithOffsets(text, start, end + 1, searchBoundaries)
    val index = normalizedHaystack.indexOf(normalizedNeedle)
    if (index < 0) return null
    val originalStart = offsets[index]
    val originalEnd = offsets[index + normalizedNeedle.lastIndex] + 1
    return originalStart until originalEnd
  }

  internal data class ElementRecord(
    val path: SourceElementPath,
    val tagName: String,
    val id: String?,
    val sameTagSiblingIndex: Int,
    val childCount: Int,
  )

  internal companion object {
    val Empty = ReaderSourceMap("", emptyMap(), emptyList(), emptySet(), emptyMap())

    fun create(blocks: List<ReaderBlock>, elements: List<ElementRecord>): ReaderSourceMap {
      val flattened = SpanFlattener.sourceStream(blocks)
      val elementsByPath = elements.associateBy { it.path }
      return ReaderSourceMap(
        text = flattened.text,
        elements = elementsByPath,
        characterPaths = flattened.sourcePaths,
        searchBoundaries = flattened.searchBoundaries,
        textRanges = buildTextRanges(flattened.sourcePaths, elementsByPath),
      )
    }

    private fun buildTextRanges(
      characterPaths: List<SourceElementPath?>,
      elements: Map<SourceElementPath, ElementRecord>,
    ): Map<SourceElementPath, IntRange> {
      val ranges = mutableMapOf<SourceElementPath, MutableTextRange>()
      characterPaths.forEachIndexed { index, path ->
        if (path != null) ranges.getOrPut(path) { MutableTextRange(index, index) }.last = index
      }
      elements.keys
        .sortedByDescending { it.childIndexes.size }
        .forEach { path ->
          val range = ranges[path] ?: return@forEach
          val parent = path.parent() ?: return@forEach
          ranges.getOrPut(parent) { MutableTextRange(range.first, range.last) }.include(range)
        }
      return ranges.mapValues { (_, range) -> range.first..range.last }
    }
  }
}

private data class MutableTextRange(var first: Int, var last: Int) {
  fun include(other: MutableTextRange) {
    first = minOf(first, other.first)
    last = maxOf(last, other.last)
  }
}

private fun SourceElementPath.parent(): SourceElementPath? =
  childIndexes.dropLast(1).takeIf { it.size < childIndexes.size }?.let(::SourceElementPath)

fun normalizeWhitespace(value: String): String =
  normalizeWhitespaceWithOffsets(value, 0, value.length, emptySet()).first

private fun normalizeWhitespaceWithOffsets(
  value: String,
  start: Int,
  endExclusive: Int,
  searchBoundaries: Set<Int>,
): Pair<String, List<Int>> {
  val normalized = StringBuilder()
  val offsets = mutableListOf<Int>()
  var inWhitespace = false

  fun appendWhitespace(offset: Int) {
    if (normalized.isNotEmpty() && !inWhitespace) {
      normalized.append(' ')
      offsets += offset
    }
    inWhitespace = true
  }

  for (index in start until endExclusive.coerceAtMost(value.length)) {
    if (index in searchBoundaries) appendWhitespace(index)
    val char = value[index]
    if (char.isWhitespace()) {
      appendWhitespace(index)
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
