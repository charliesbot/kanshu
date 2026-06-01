package com.charliesbot.kanshu.features.reader

import android.util.Log
import com.charliesbot.kanshu.navigator.model.LinkSpan
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.ParseDiagnostics
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.model.StyledGroup
import com.charliesbot.kanshu.navigator.model.TextLeaf
import com.charliesbot.kanshu.navigator.model.TextSpan
import com.charliesbot.kanshu.navigator.parser.EpubParser
import org.readium.r2.shared.publication.Publication

private const val TAG = "ReaderSpine"

internal data class SpineItem(
  val spineIndex: Int,
  val document: ReaderDocument,
  val diagnostics: ParseDiagnostics,
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

internal suspend fun Publication.readFirstSpineItem(): ReaderDocument? =
  readNextSpineItem(afterSpineIndex = -1)?.document

internal suspend fun Publication.readNextSpineItem(afterSpineIndex: Int): SpineItem? {
  val index = afterSpineIndex + 1
  Log.d(TAG, "reading spine[$index] after spine[$afterSpineIndex] of ${readingOrder.size}")
  val link = readingOrder.getOrNull(index)
  if (link == null) {
    Log.d(TAG, "spine[$index]: no next link in readingOrder")
    return null
  }
  val xhtml = readSpineXhtml(index) ?: return null
  val parseResult = EpubParser.parse(xhtml)
  val document = parseResult.document
  val flattened = document.flattenedText()
  val textLength = flattened.length
  Log.d(
    TAG,
    "spine[$index]: parsed blocks=${document.blocks.size} chars=$textLength language=${document.language}",
  )
  Log.d(
    TAG,
    "using spine[$index] href=${link.href} blocks=${document.blocks.size} chars=$textLength",
  )
  return SpineItem(spineIndex = index, document = document, diagnostics = parseResult.diagnostics)
}

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
