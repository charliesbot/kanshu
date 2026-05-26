package com.charliesbot.kanshu.features.reader

import android.util.Log
import com.charliesbot.kanshu.navigator.model.LinkSpan
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.model.StyledGroup
import com.charliesbot.kanshu.navigator.model.TextLeaf
import com.charliesbot.kanshu.navigator.model.TextSpan
import com.charliesbot.kanshu.navigator.parser.EpubParser
import org.readium.r2.shared.publication.Publication

private const val TAG = "ReaderSpine"

/** Minimum flattened text before a spine item counts as readable chapter content. */
private const val MIN_READABLE_CHARS = 40

internal data class ReadableSpineItem(val spineIndex: Int, val document: ReaderDocument)

private val SKIP_SPINE_HREF =
  Regex(
    pattern = """(cover|titlepage|title-page|half-title|halftitle|frontcover|front-cover)""",
    option = RegexOption.IGNORE_CASE,
  )

internal suspend fun Publication.readSpineXhtml(spineIndex: Int = 0): String? {
  val link = readingOrder.getOrNull(spineIndex)
  if (link == null) {
    Log.d(TAG, "spine[$spineIndex]: no link in readingOrder (size=${readingOrder.size})")
    return null
  }
  val resource = get(link)
  if (resource == null) {
    Log.d(TAG, "spine[$spineIndex]: get(link) returned null href=${link.href}")
    return null
  }
  val bytes = resource.read().getOrNull()
  if (bytes == null) {
    Log.d(TAG, "spine[$spineIndex]: resource.read() failed href=${link.href}")
    return null
  }
  val xhtml = bytes.decodeToString()
  Log.d(TAG, "spine[$spineIndex]: read ${xhtml.length} chars href=${link.href}")
  return xhtml
}

/**
 * First spine item with parseable paragraph content (skips cover pages, image-only spreads, and
 * empty front matter).
 */
internal suspend fun Publication.readFirstReadableChapter(): ReaderDocument? =
  readNextReadableSpineItem(afterSpineIndex = -1)?.document

internal suspend fun Publication.readNextReadableSpineItem(
  afterSpineIndex: Int
): ReadableSpineItem? {
  Log.d(TAG, "scanning ${readingOrder.size} spine items after spine[$afterSpineIndex]")
  val startIndex = (afterSpineIndex + 1).coerceAtLeast(0)
  for (index in startIndex until readingOrder.size) {
    val link = readingOrder[index]
    if (shouldSkipSpineHref(link.href.toString())) {
      Log.d(TAG, "spine[$index]: skipping cover-like href=${link.href}")
      continue
    }

    val xhtml = readSpineXhtml(index) ?: continue
    val document = EpubParser.parse(xhtml).document
    val flattened = document.flattenedText()
    val textLength = flattened.length
    Log.d(
      TAG,
      "spine[$index]: parsed blocks=${document.blocks.size} chars=$textLength language=${document.language}",
    )

    if (document.blocks.isEmpty()) continue

    if (textLength < MIN_READABLE_CHARS) {
      Log.d(TAG, "spine[$index]: skipping short text ($textLength chars)")
      continue
    }

    Log.d(
      TAG,
      "using spine[$index] href=${link.href} blocks=${document.blocks.size} chars=$textLength",
    )
    return ReadableSpineItem(spineIndex = index, document = document)
  }
  Log.d(TAG, "no spine item produced readable blocks")
  return null
}

private fun shouldSkipSpineHref(href: String): Boolean = SKIP_SPINE_HREF.containsMatchIn(href)

private fun ReaderDocument.flattenedText(): String =
  blocks.filterIsInstance<ParagraphBlock>().flatMap { block -> block.spans }.flattenedText().trim()

private fun List<TextSpan>.flattenedText(): String =
  joinToString("") { span -> span.flattenedText() }

private fun TextSpan.flattenedText(): String =
  when (this) {
    is TextLeaf -> text
    is StyledGroup -> children.flattenedText()
    is LinkSpan -> children.flattenedText()
  }
