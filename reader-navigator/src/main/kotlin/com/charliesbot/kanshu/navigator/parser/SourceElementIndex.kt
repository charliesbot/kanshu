package com.charliesbot.kanshu.navigator.parser

import com.charliesbot.kanshu.core.reader.SourceElementPath
import com.charliesbot.kanshu.navigator.ReaderSourceMap
import com.charliesbot.kanshu.navigator.model.ReaderBlock
import java.util.IdentityHashMap
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

/** Indexes the XHTML element tree once and owns all DOM-to-source-path translation. */
internal class SourceElementIndex(
  private val elements: List<ReaderSourceMap.ElementRecord>,
  private val paths: Map<Element, SourceElementPath>,
) {
  fun pathOf(node: Node): SourceElementPath? {
    val element = if (node is Element) node else node.parent() as? Element
    return element?.let(paths::get)
  }

  fun sourceMap(blocks: List<ReaderBlock>): ReaderSourceMap =
    ReaderSourceMap.create(blocks, elements)

  companion object {
    fun create(body: Element): SourceElementIndex {
      val elements = mutableListOf<ReaderSourceMap.ElementRecord>()
      val paths = IdentityHashMap<Element, SourceElementPath>()

      fun index(element: Element, path: SourceElementPath, sameTagSiblingIndex: Int) {
        paths[element] = path
        elements +=
          ReaderSourceMap.ElementRecord(
            path = path,
            tagName = element.tagName().lowercase(),
            id = element.id().ifBlank { null },
            sameTagSiblingIndex = sameTagSiblingIndex,
            childPaths = element.children().indices.map(path::child),
          )

        val siblingCounts = mutableMapOf<String, Int>()
        element.children().forEachIndexed { childIndex, child ->
          val tagName = child.tagName().lowercase()
          val siblingIndex = siblingCounts.getOrDefault(tagName, 0)
          siblingCounts[tagName] = siblingIndex + 1
          index(child, path.child(childIndex), siblingIndex)
        }
      }

      index(body, SourceElementPath.Root, sameTagSiblingIndex = 0)
      return SourceElementIndex(elements, paths)
    }
  }
}

private fun SourceElementPath.child(index: Int): SourceElementPath =
  SourceElementPath(childIndexes + index)
