package com.charliesbot.kanshu.navigator.model

data class ReaderDocument(val blocks: List<ReaderBlock>)

sealed interface ReaderBlock

data class ParagraphBlock(val spans: List<TextSpan>) : ReaderBlock

data class HeadingBlock(val level: Int, val spans: List<TextSpan>) : ReaderBlock

data class QuoteBlock(val children: List<ReaderBlock>) : ReaderBlock

data class ListBlock(val ordered: Boolean, val items: List<ListItem>) : ReaderBlock

data class ListItem(val blocks: List<ReaderBlock>)

data object HorizontalRule : ReaderBlock

data class ImageBlock(val resourceHref: String, val alt: String?) : ReaderBlock

data class UnsupportedBlock(val tagName: String) : ReaderBlock
