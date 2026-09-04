package com.charliesbot.kanshu.navigator.model

import com.charliesbot.kanshu.core.reader.SourceElementPath

sealed interface TextSpan

class TextLeaf(
  val text: String,
  val style: InlineStyle = InlineStyle.Plain,
  internal val sourceElementPath: SourceElementPath? = null,
) : TextSpan {
  override fun equals(other: Any?): Boolean =
    other is TextLeaf && text == other.text && style == other.style

  override fun hashCode(): Int = 31 * text.hashCode() + style.hashCode()

  override fun toString(): String = "TextLeaf(text=$text, style=$style)"
}

data class StyledGroup(val style: InlineStyle, val children: List<TextSpan>) : TextSpan

data class LinkSpan(val href: String, val children: List<TextSpan>) : TextSpan

enum class InlineStyle {
  Plain,
  Bold,
  Italic,
  BoldItalic,
  SmallCaps,
}
