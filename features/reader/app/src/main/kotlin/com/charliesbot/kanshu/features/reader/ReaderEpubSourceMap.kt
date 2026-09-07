package com.charliesbot.kanshu.features.reader

import com.charliesbot.kanshu.core.provider.EpubSourceElement
import com.charliesbot.kanshu.core.provider.EpubSourceMap
import com.charliesbot.kanshu.core.reader.SourceElementPath
import com.charliesbot.kanshu.navigator.ReaderSourceMap

internal class ReaderEpubSourceMap(private val sourceMap: ReaderSourceMap) : EpubSourceMap {
  override fun inspect(path: SourceElementPath): EpubSourceElement? =
    sourceMap.inspect(path)?.let {
      EpubSourceElement(
        path = it.path,
        tagName = it.tagName,
        id = it.id,
        sameTagSiblingIndex = it.sameTagSiblingIndex,
        childPaths = it.childPaths,
        textRange = it.textRange,
      )
    }

  override fun resolveChild(
    parent: SourceElementPath,
    elementChildIndex: Int,
  ): SourceElementPath? = sourceMap.resolveChild(parent, elementChildIndex)

  override fun resolveElementId(id: String): SourceElementPath? = sourceMap.resolveElementId(id)

  override fun findFirstLiteralMatch(
    startElementPath: SourceElementPath,
    endElementPath: SourceElementPath,
    selectedText: String,
  ): IntRange? = sourceMap.findFirstLiteralMatch(startElementPath, endElementPath, selectedText)
}
