package com.charliesbot.kanshu.navigator.selection

import com.charliesbot.kanshu.navigator.render.SelectionPageTurnDirection

internal data class SelectionCarryState(
  val prefixPages: List<String> = emptyList(),
  val suffixPages: List<String> = emptyList(),
  val pageSelections: Map<Int, TextSelection> = emptyMap(),
)

internal data class SelectionPageTurnState(
  val carryState: SelectionCarryState,
  val targetPage: Int,
  val seedAtPageEnd: Boolean,
  val restoredSelection: TextSelection?,
)

internal fun SelectionCarryState.turnSelectionPage(
  direction: SelectionPageTurnDirection,
  currentPage: Int,
  lastPageIndex: Int,
  pageSelectedText: String,
  currentSelection: TextSelection,
): SelectionPageTurnState? =
  when (direction) {
    SelectionPageTurnDirection.Previous ->
      turnToPreviousSelectionPage(
        currentPage = currentPage,
        pageSelectedText = pageSelectedText,
        currentSelection = currentSelection,
      )
    SelectionPageTurnDirection.Next ->
      turnToNextSelectionPage(
        currentPage = currentPage,
        lastPageIndex = lastPageIndex,
        pageSelectedText = pageSelectedText,
        currentSelection = currentSelection,
      )
  }

private fun SelectionCarryState.turnToPreviousSelectionPage(
  currentPage: Int,
  pageSelectedText: String,
  currentSelection: TextSelection,
): SelectionPageTurnState? {
  if (currentPage <= 0) return null

  val targetPage = currentPage - 1
  val restoredSelection = pageSelections[targetPage]
  return SelectionPageTurnState(
    carryState =
      copy(
        prefixPages = prefixPages.dropLast(1),
        suffixPages = previousPageSuffix(pageSelectedText),
        pageSelections = saveSelection(currentPage, currentSelection),
      ),
    targetPage = targetPage,
    seedAtPageEnd = restoredSelection == null,
    restoredSelection = restoredSelection,
  )
}

private fun SelectionCarryState.turnToNextSelectionPage(
  currentPage: Int,
  lastPageIndex: Int,
  pageSelectedText: String,
  currentSelection: TextSelection,
): SelectionPageTurnState? {
  if (currentPage >= lastPageIndex) return null

  val targetPage = currentPage + 1
  return SelectionPageTurnState(
    carryState =
      copy(
        prefixPages = prefixPages + pageSelectedText,
        suffixPages = emptyList(),
        pageSelections = saveSelection(currentPage, currentSelection),
      ),
    targetPage = targetPage,
    seedAtPageEnd = false,
    restoredSelection = pageSelections[targetPage],
  )
}

private fun SelectionCarryState.previousPageSuffix(pageSelectedText: String): List<String> =
  if (prefixPages.isEmpty()) listOf(pageSelectedText) + suffixPages else emptyList()

private fun SelectionCarryState.saveSelection(
  pageIndex: Int,
  selection: TextSelection,
): Map<Int, TextSelection> = pageSelections + (pageIndex to selection)

internal fun List<String>.toSelectionTextPrefix(): String =
  if (isEmpty()) "" else joinToString(separator = "\n\n", postfix = "\n\n")

internal fun List<String>.toSelectionTextSuffix(): String =
  if (isEmpty()) "" else joinToString(separator = "\n\n", prefix = "\n\n")
