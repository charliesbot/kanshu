package com.charliesbot.kanshu.navigator

import com.charliesbot.kanshu.core.reader.SourceElementPath
import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.model.HorizontalRule
import com.charliesbot.kanshu.navigator.model.ImageBlock
import com.charliesbot.kanshu.navigator.model.LinkSpan
import com.charliesbot.kanshu.navigator.model.ListBlock
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.QuoteBlock
import com.charliesbot.kanshu.navigator.model.ReaderBlock
import com.charliesbot.kanshu.navigator.model.StyledGroup
import com.charliesbot.kanshu.navigator.model.TextLeaf
import com.charliesbot.kanshu.navigator.model.TextSpan

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
      val builder = SourceTextBuilder()
      blocks.forEach { block ->
        if (block is ListBlock) builder.appendLayoutList(block) else builder.appendBlock(block)
      }
      return ReaderSourceMap(
        text = builder.text.toString(),
        elements = elements.associateBy { it.path },
        characterPaths = builder.paths,
      )
    }
  }
}

private class SourceTextBuilder {
  val text = StringBuilder()
  val paths = mutableListOf<SourceElementPath?>()

  private fun append(value: String, path: SourceElementPath? = null) {
    text.append(value)
    repeat(value.length) { paths += path }
  }

  fun appendBlock(block: ReaderBlock) {
    when (block) {
      is HeadingBlock -> appendSpans(block.spans)
      is ParagraphBlock -> appendSpans(block.spans)
      is QuoteBlock -> appendBlocks(block.children, "\n\n")
      is ListBlock ->
        block.items.forEachIndexed { index, item ->
          if (index > 0) append("\n")
          appendBlocks(item.blocks, "\n\n")
        }
      is HorizontalRule,
      is ImageBlock -> Unit
    }
  }

  fun appendLayoutList(block: ListBlock) {
    block.items.forEach { item ->
      val buffered = mutableListOf<ReaderBlock>()
      fun flush() {
        appendBlocks(buffered, "\n\n")
        buffered.clear()
      }
      item.blocks.forEach { child ->
        if (child is ListBlock) {
          flush()
          appendLayoutList(child)
        } else {
          buffered += child
        }
      }
      flush()
    }
  }

  private fun appendBlocks(blocks: List<ReaderBlock>, separator: String) {
    var emitted = false
    blocks.forEach { block ->
      val before = text.length
      if (emitted) append(separator)
      appendBlock(block)
      if (text.length == before + if (emitted) separator.length else 0) {
        if (emitted) {
          text.delete(text.length - separator.length, text.length)
          repeat(separator.length) { paths.removeAt(paths.lastIndex) }
        }
      } else {
        emitted = true
      }
    }
  }

  private fun appendSpans(spans: List<TextSpan>) {
    spans.forEach(::appendSpan)
  }

  private fun appendSpan(span: TextSpan) {
    when (span) {
      is TextLeaf -> append(span.text, span.sourceElementPath)
      is StyledGroup -> span.children.forEach(::appendSpan)
      is LinkSpan -> span.children.forEach(::appendSpan)
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
