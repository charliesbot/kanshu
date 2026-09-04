package com.charliesbot.kanshu.navigator.model

import com.charliesbot.kanshu.navigator.ReaderSourceMap

data class ReaderDocument(
  val blocks: List<ReaderBlock>,
  val language: String? = null,
  val sourceMap: ReaderSourceMap = ReaderSourceMap.Empty,
)

/**
 * Block AST for the native reader engine.
 *
 * Native rendering is intentionally staged: paragraphs, headings, rules, quotes, and lists are
 * active today; images are scaffolded for a later parser and layout slice.
 */
sealed interface ReaderBlock

data class ParagraphBlock(
  val spans: List<TextSpan>,
  val alignment: BlockAlignment? = null,
  val spacing: BlockSpacing? = null,
) : ReaderBlock

data class HeadingBlock(
  val level: Int,
  val spans: List<TextSpan>,
  val alignment: BlockAlignment? = null,
  val spacing: BlockSpacing? = null,
) : ReaderBlock

data class QuoteBlock(val children: List<ReaderBlock>) : ReaderBlock

data class ListBlock(val ordered: Boolean, val items: List<ListItem>) : ReaderBlock

data class ListItem(val blocks: List<ReaderBlock>)

data object HorizontalRule : ReaderBlock

data class ImageBlock(val resourceHref: String, val alt: String?) : ReaderBlock
