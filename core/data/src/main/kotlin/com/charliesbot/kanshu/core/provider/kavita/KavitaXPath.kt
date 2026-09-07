package com.charliesbot.kanshu.core.provider.kavita

import com.charliesbot.kanshu.core.provider.ProviderSourceMap
import com.charliesbot.kanshu.core.reader.SourceElementPath

internal fun resolveKavitaXPath(
  xpath: String,
  sourceMap: ProviderSourceMap,
): SourceElementPath? {
  val anchor = parseKavitaAnchor(xpath) ?: return null
  var current =
    when (val root = anchor.root) {
      KavitaAnchorRoot.Body -> SourceElementPath.Root
      is KavitaAnchorRoot.ElementId -> sourceMap.resolveElementId(root.id) ?: return null
    }
  for (step in anchor.steps) {
    val parent = sourceMap.inspect(current) ?: return null
    current =
      parent.childPaths
        .asSequence()
        .mapNotNull(sourceMap::inspect)
        .firstOrNull {
          it.tagName.equals(step.tagName, ignoreCase = true) &&
            it.sameTagSiblingIndex == step.siblingIndex
        }
        ?.path ?: return null
  }
  return current.takeIf { sourceMap.inspect(it) != null }
}

private sealed interface KavitaAnchorRoot {
  data object Body : KavitaAnchorRoot

  data class ElementId(val id: String) : KavitaAnchorRoot
}

private data class KavitaChildStep(val tagName: String, val siblingIndex: Int)

private data class KavitaAnchor(val root: KavitaAnchorRoot, val steps: List<KavitaChildStep>)

// This is Kavita's element-anchor subset, not a general XPath evaluator.
// Only a leading //body (or //html/body) is supported; descendant steps,
// namespaces, wildcards, text nodes, and additional predicates are rejected.
private val bodyRoot =
  Regex("""^/{1,2}(?:html(?:\[1\])?/)?body(?:\[1\])?(?=/|$)""", RegexOption.IGNORE_CASE)
private val idRoot = Regex("""^id\((?:"([^"]+)"|'([^']+)')\)(?=/|$)""")
private val childStep = Regex("""([A-Za-z][A-Za-z0-9_-]*)(?:\[([0-9]+)\])?""")

private fun parseKavitaAnchor(xpath: String): KavitaAnchor? {
  val input = xpath.trim()
  val bodyMatch = bodyRoot.find(input)
  val idMatch = if (bodyMatch == null) idRoot.find(input) else null
  val root: KavitaAnchorRoot
  val rootEnd: Int
  when {
    bodyMatch != null -> {
      root = KavitaAnchorRoot.Body
      rootEnd = bodyMatch.range.last + 1
    }
    idMatch != null -> {
      val id = idMatch.groups[1]?.value ?: idMatch.groups[2]?.value ?: return null
      // XPath id() accepts whitespace-separated IDs; our anchor must identify one element.
      if (id.any(Char::isWhitespace)) return null
      root = KavitaAnchorRoot.ElementId(id)
      rootEnd = idMatch.range.last + 1
    }
    else -> return null
  }
  val suffix = input.substring(rootEnd)
  if (suffix.isEmpty()) return KavitaAnchor(root, emptyList())

  val steps =
    suffix.substring(1).split('/').map { segment ->
      val match = childStep.matchEntire(segment) ?: return null
      val index = match.groups[2]?.let { it.value.toIntOrNull() ?: return null } ?: 1
      if (index < 1) return null
      KavitaChildStep(match.groupValues[1], index - 1)
    }
  return KavitaAnchor(root, steps)
}
