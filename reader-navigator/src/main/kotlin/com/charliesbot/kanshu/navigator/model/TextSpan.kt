package com.charliesbot.kanshu.navigator.model

sealed interface TextSpan

data class TextLeaf(val text: String, val style: InlineStyle = InlineStyle.Plain) : TextSpan

data class StyledGroup(val style: InlineStyle, val children: List<TextSpan>) : TextSpan

data class LinkSpan(val href: String, val children: List<TextSpan>) : TextSpan

enum class InlineStyle {
  Plain,
  Bold,
  Italic,
  BoldItalic,
  SmallCaps,
}
