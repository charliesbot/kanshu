package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.navigator.model.LinkSpan
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import com.charliesbot.kanshu.navigator.model.StyledGroup
import com.charliesbot.kanshu.navigator.model.TextLeaf
import com.charliesbot.kanshu.navigator.model.TextSpan

internal fun loadFixture(name: String): String =
  checkNotNull(EpubParser::class.java.classLoader?.getResourceAsStream("fixtures/$name")) {
      "Missing fixture: $name"
    }
    .bufferedReader()
    .readText()

internal fun ReaderDocument.paragraphText(): List<String> =
  blocks.filterIsInstance<ParagraphBlock>().map { block ->
    block.spans.joinToString("") { spanText(it) }
  }

internal fun spanText(span: TextSpan): String =
  when (span) {
    is TextLeaf -> span.text
    is LinkSpan -> span.children.joinToString("") { spanText(it) }
    is StyledGroup -> span.children.joinToString("") { spanText(it) }
  }
