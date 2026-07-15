package com.charliesbot.kanshu.navigator.selection

import android.graphics.RectF
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import com.charliesbot.kanshu.navigator.engine.EpubLinkSpan
import com.charliesbot.kanshu.navigator.engine.PageEntry
import com.charliesbot.kanshu.navigator.engine.ReaderPage
import java.text.BreakIterator
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class TextPosition(val blockIndex: Int, val offset: Int)

internal data class TextSelection(
  val blockIndex: Int,
  val entryIndex: Int,
  val anchorRange: IntRange,
  val range: IntRange,
  val text: String,
  val rects: List<RectF>,
  val anchor: RectF,
  val focusEntryIndex: Int = entryIndex,
  val focusRange: IntRange = range,
)

internal object ReaderSelector {
  private val textStringCache: MutableMap<CharSequence, String> =
    Collections.synchronizedMap(WeakHashMap())
  private val breakIteratorCache =
    ThreadLocal.withInitial<MutableMap<Locale, BreakIterator>> { mutableMapOf() }

  private data class SelectionEndpoint(val entryIndex: Int, val range: IntRange)

  private data class SelectionSegment(val entryIndex: Int, val range: IntRange)

  private data class TextHit(
    val entryIndex: Int,
    val entry: PageEntry,
    val layout: StaticLayout,
    val line: Int,
    val localX: Float,
  )

  /**
   * The link href at a tap position, or null when the tap is not on link text. Same geometry
   * pipeline as selection (docs/PRD_NATIVE_READER.md § Link and Footnote Taps): strict bounds, no
   * clamping — a miss falls through to the tap zones.
   */
  fun linkHrefAt(
    page: ReaderPage,
    xPx: Float,
    yPx: Float,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
  ): String? {
    val hit =
      page.textHitAt(
        xPx = xPx,
        yPx = yPx,
        horizontalMarginPx = horizontalMarginPx,
        verticalMarginPx = verticalMarginPx,
        clampXToLine = false,
      ) ?: return null
    val spanned = hit.layout.text as? Spanned ?: return null
    val offset = hit.layout.getOffsetForHorizontal(hit.line, hit.localX)
    return spanned.getSpans(offset, offset, EpubLinkSpan::class.java).firstOrNull()?.href
  }

  fun startSelectionAt(
    page: ReaderPage,
    xPx: Float,
    yPx: Float,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
    locale: Locale = Locale.getDefault(),
  ): TextSelection? {
    val hit =
      page.textHitAt(
        xPx = xPx,
        yPx = yPx,
        horizontalMarginPx = horizontalMarginPx,
        verticalMarginPx = verticalMarginPx,
        clampXToLine = false,
      ) ?: return null
    val range = hit.wordRangeAt(locale) ?: return null
    return page.selectionFrom(
      anchor = SelectionEndpoint(hit.entryIndex, range),
      focus = SelectionEndpoint(hit.entryIndex, range),
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
    )
  }

  fun updateSelectionTo(
    page: ReaderPage,
    selection: TextSelection,
    xPx: Float,
    yPx: Float,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
    locale: Locale = Locale.getDefault(),
  ): TextSelection? {
    val hit =
      page.textHitAt(
        xPx = xPx,
        yPx = yPx,
        horizontalMarginPx = horizontalMarginPx,
        verticalMarginPx = verticalMarginPx,
        clampXToLine = true,
      ) ?: return null
    if (
      hit.entryIndex != selection.entryIndex &&
        hit.entry.selectionId == page.entries[selection.entryIndex].selectionId
    ) {
      return null
    }

    val currentRange = hit.nearestWordRangeAt(locale) ?: return null
    return page.selectionFrom(
      anchor = SelectionEndpoint(selection.entryIndex, selection.anchorRange),
      focus = SelectionEndpoint(hit.entryIndex, currentRange),
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
    )
  }

  fun startSelectionAtPageStart(
    page: ReaderPage,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
    locale: Locale = Locale.getDefault(),
  ): TextSelection? =
    page.pageEdgeSelection(
      atEnd = false,
      anchor = null,
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
      locale = locale,
    )

  fun startSelectionAtPageEnd(
    page: ReaderPage,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
    locale: Locale = Locale.getDefault(),
  ): TextSelection? =
    page.pageEdgeSelection(
      atEnd = true,
      anchor = null,
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
      locale = locale,
    )

  fun updateSelectionToPageStart(
    page: ReaderPage,
    selection: TextSelection,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
    locale: Locale = Locale.getDefault(),
  ): TextSelection? =
    page.pageEdgeSelection(
      atEnd = false,
      anchor = SelectionEndpoint(selection.entryIndex, selection.anchorRange),
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
      locale = locale,
    )

  fun updateSelectionToPageEnd(
    page: ReaderPage,
    selection: TextSelection,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
    locale: Locale = Locale.getDefault(),
  ): TextSelection? =
    page.pageEdgeSelection(
      atEnd = true,
      anchor = SelectionEndpoint(selection.entryIndex, selection.anchorRange),
      horizontalMarginPx = horizontalMarginPx,
      verticalMarginPx = verticalMarginPx,
      locale = locale,
    )

  /**
   * Selection whose focus is the first word on the page (`atEnd = false`) or the last (`atEnd =
   * true`). A null [anchor] seeds a fresh selection at the found word; otherwise the found word
   * extends the existing anchor.
   */
  private fun ReaderPage.pageEdgeSelection(
    atEnd: Boolean,
    anchor: SelectionEndpoint?,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
    locale: Locale,
  ): TextSelection? {
    val indexedEntries = entries.withIndex().let { if (atEnd) it.reversed() else it.toList() }
    for ((entryIndex, entry) in indexedEntries) {
      val layout = entry.textLayout() ?: continue
      val lines = entry.visibleLineRange(layout).let { if (atEnd) it.reversed() else it }
      for (line in lines) {
        val hit =
          TextHit(
            entryIndex = entryIndex,
            entry = entry,
            layout = layout,
            line = line,
            localX = if (atEnd) layout.getLineRight(line) else layout.getLineLeft(line),
          )
        val matches = hit.wordRangesOnLine(locale)
        val range = (if (atEnd) matches.lastOrNull() else matches.firstOrNull())?.range ?: continue
        val focus = SelectionEndpoint(entryIndex, range)
        return selectionFrom(
          anchor = anchor ?: focus,
          focus = focus,
          horizontalMarginPx = horizontalMarginPx,
          verticalMarginPx = verticalMarginPx,
        )
      }
    }
    return null
  }

  private fun ReaderPage.textHitAt(
    xPx: Float,
    yPx: Float,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
    clampXToLine: Boolean,
  ): TextHit? {
    val indexedEntry =
      entries.withIndex().firstOrNull { (_, entry) ->
        val entryTop = verticalMarginPx + entry.yOffsetPx
        yPx >= entryTop && yPx <= entryTop + entry.visibleHeightPx
      } ?: return null
    val entry = indexedEntry.value

    val layout = entry.textLayout() ?: return null
    val rawLocalX = xPx - horizontalMarginPx - entry.drawOffsetXPx
    if (!clampXToLine && (rawLocalX < 0f || rawLocalX > layout.width)) return null
    val boundedLocalX =
      if (clampXToLine) rawLocalX.coerceIn(0f, layout.width.toFloat()) else rawLocalX

    val localY =
      when (entry) {
        is PageEntry.FullBlock -> yPx - verticalMarginPx - entry.yOffsetPx
        is PageEntry.SplitBlock -> yPx - verticalMarginPx - entry.yOffsetPx + entry.firstLineTopPx
        is PageEntry.HorizontalRule -> return null
        is PageEntry.Image -> return null
      }
    if (localY < 0f || localY > layout.height) return null

    val line = layout.getLineForVertical(localY.toInt())
    if (entry is PageEntry.SplitBlock && line !in entry.lineRange) return null
    val lineLeft = layout.getLineLeft(line)
    val lineRight = layout.getLineRight(line)
    if (!clampXToLine && (boundedLocalX < lineLeft || boundedLocalX > lineRight)) return null
    val localX = if (clampXToLine) boundedLocalX.coerceIn(lineLeft, lineRight) else boundedLocalX

    return TextHit(
      entryIndex = indexedEntry.index,
      entry = entry,
      layout = layout,
      line = line,
      localX = localX,
    )
  }

  private fun PageEntry.textLayout(): StaticLayout? =
    when (this) {
      is PageEntry.FullBlock -> layout
      is PageEntry.SplitBlock -> layout
      is PageEntry.HorizontalRule -> null
      is PageEntry.Image -> null
    }

  private fun TextHit.wordRangeAt(locale: Locale): IntRange? {
    val match = nearestWordMatchAtOffset(locale) ?: return null
    return match.range.takeIf { match.bounds.containsHorizontal(localX) }
  }

  private fun TextHit.nearestWordRangeAt(locale: Locale): IntRange? {
    return nearbyWordMatches(locale)
      .filter { match -> match.bounds.width() > 0f }
      .minByOrNull { match ->
        when {
          match.bounds.containsHorizontal(localX) -> 0f
          localX < match.bounds.left -> abs(match.bounds.left - localX)
          else -> abs(localX - match.bounds.right)
        }
      }
      ?.range
  }

  private data class WordMatch(val range: IntRange, val bounds: RectF)

  private fun TextHit.wordRangesOnLine(locale: Locale): List<WordMatch> {
    val iterator = breakIterator(locale)
    val text = layout.text.cachedString()
    iterator.setText(text)
    val lineStart = layout.getLineStart(line).coerceIn(0, text.length)
    val lineEnd = layout.getLineEnd(line).coerceIn(lineStart, text.length)
    val matches = mutableListOf<WordMatch>()
    var start = if (lineStart == 0) iterator.first() else iterator.following(lineStart - 1)
    while (start != BreakIterator.DONE && start < lineEnd) {
      val end = iterator.following(start)
      if (end == BreakIterator.DONE) return matches
      if (end <= start) break
      val startOffset = max(start, lineStart)
      val endOffset = min(end, lineEnd)
      if (startOffset < endOffset && text.hasLetterOrDigit(start, end)) {
        matches +=
          WordMatch(
            range = start until end,
            bounds = layout.horizontalBounds(line, startOffset, endOffset, entry.textJustified),
          )
      }
      start = end
    }
    return matches
  }

  private fun TextHit.nearestWordMatchAtOffset(locale: Locale): WordMatch? =
    nearbyWordMatches(locale).firstOrNull { match -> match.bounds.containsHorizontal(localX) }

  private fun TextHit.nearbyWordMatches(locale: Locale): List<WordMatch> {
    val text = layout.text.cachedString()
    val lineStart = layout.getLineStart(line).coerceIn(0, text.length)
    val lineEnd = layout.getLineEnd(line).coerceIn(lineStart, text.length)
    if (lineStart >= lineEnd) return emptyList()

    val offset = layout.getOffsetForHorizontal(line, localX).coerceIn(lineStart, lineEnd)
    val iterator = breakIterator(locale)
    iterator.setText(text)
    val candidates =
      listOfNotNull(
          iterator.wordContaining(text, offset, lineStart, lineEnd),
          iterator.wordBefore(text, offset, lineStart),
          iterator.wordAfter(text, offset, lineEnd),
        )
        .distinct()

    return candidates.mapNotNull { range ->
      val startOffset = max(range.first, lineStart)
      val endOffset = min(range.last + 1, lineEnd)
      if (startOffset >= endOffset) return@mapNotNull null
      WordMatch(
        range = range,
        bounds = layout.horizontalBounds(line, startOffset, endOffset, entry.textJustified),
      )
    }
  }

  private fun BreakIterator.wordContaining(
    text: CharSequence,
    offset: Int,
    lineStart: Int,
    lineEnd: Int,
  ): IntRange? =
    listOf(offset, offset - 1, offset + 1).firstNotNullOfOrNull { probe ->
      if (probe !in lineStart until lineEnd) return@firstNotNullOfOrNull null
      val start = preceding(probe + 1)
      if (start == BreakIterator.DONE) return@firstNotNullOfOrNull null
      val end = following(start)
      if (end <= start) return@firstNotNullOfOrNull null
      wordRangeIfValid(text, start, end, lineStart, lineEnd)
    }

  private fun BreakIterator.wordBefore(text: CharSequence, offset: Int, lineStart: Int): IntRange? {
    var end = preceding(offset.coerceIn(lineStart + 1, text.length))
    while (end != BreakIterator.DONE && end > lineStart) {
      val start = preceding(end)
      if (start == BreakIterator.DONE || start >= end) break
      val range = wordRangeIfValid(text, start, end, lineStart, offset)
      if (range != null) return range
      end = start
    }
    return null
  }

  private fun BreakIterator.wordAfter(text: CharSequence, offset: Int, lineEnd: Int): IntRange? {
    var start = following((offset - 1).coerceIn(0, text.length))
    while (start != BreakIterator.DONE && start < lineEnd) {
      val end = following(start)
      if (end == BreakIterator.DONE || end <= start) break
      val range = wordRangeIfValid(text, start, end, offset, lineEnd)
      if (range != null) return range
      start = end
    }
    return null
  }

  private fun BreakIterator.wordRangeIfValid(
    text: CharSequence,
    start: Int,
    end: Int,
    minOffset: Int,
    maxOffset: Int,
  ): IntRange? {
    if (start == BreakIterator.DONE || end == BreakIterator.DONE) return null
    if (start >= end || end <= minOffset || start >= maxOffset) return null
    return (start until end).takeIf { text.hasLetterOrDigit(start, end) }
  }

  private fun ReaderPage.selectionFrom(
    anchor: SelectionEndpoint,
    focus: SelectionEndpoint,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
  ): TextSelection? {
    val segments = selectionSegments(anchor, focus)
    if (segments.hasDuplicateTextSelectionId(this)) return null
    val rects = segments.flatMap { segment ->
      val entry = entries[segment.entryIndex]
      val layout = entry.textLayout() ?: return@flatMap emptyList()
      selectionRects(layout, segment.range, entry, horizontalMarginPx, verticalMarginPx)
    }
    if (rects.isEmpty()) return null
    val firstSegment = segments.first()
    val anchorEntry = entries[anchor.entryIndex]
    return TextSelection(
      blockIndex = anchorEntry.blockIndex,
      entryIndex = anchor.entryIndex,
      anchorRange = anchor.range,
      range = firstSegment.range,
      text = selectedText(segments),
      rects = rects,
      anchor = rects.union(),
      focusEntryIndex = focus.entryIndex,
      focusRange = focus.range,
    )
  }

  private fun ReaderPage.selectionSegments(
    anchor: SelectionEndpoint,
    focus: SelectionEndpoint,
  ): List<SelectionSegment> {
    val first = min(anchor.entryIndex, focus.entryIndex)
    val last = max(anchor.entryIndex, focus.entryIndex)
    val forward = anchor.entryIndex <= focus.entryIndex
    return (first..last).mapNotNull { entryIndex ->
      val entry = entries[entryIndex]
      val layout = entry.textLayout() ?: return@mapNotNull null
      val visibleRange = entry.visibleTextRange(layout) ?: return@mapNotNull null
      val range = selectionRange(entryIndex, visibleRange, anchor, focus, forward)
      if (range.first <= range.last) SelectionSegment(entryIndex, range) else null
    }
  }

  private fun selectionRange(
    entryIndex: Int,
    visibleRange: IntRange,
    anchor: SelectionEndpoint,
    focus: SelectionEndpoint,
    forward: Boolean,
  ): IntRange =
    when {
      anchor.entryIndex == focus.entryIndex ->
        min(anchor.range.first, focus.range.first)..max(anchor.range.last, focus.range.last)
      entryIndex == anchor.entryIndex ->
        if (forward) anchor.range.first..visibleRange.last
        else visibleRange.first..anchor.range.last
      entryIndex == focus.entryIndex ->
        if (forward) visibleRange.first..focus.range.last else focus.range.first..visibleRange.last
      else -> visibleRange
    }

  private fun List<SelectionSegment>.hasDuplicateTextSelectionId(page: ReaderPage): Boolean {
    val seen = mutableSetOf<Int>()
    return any { segment ->
      val entry = page.entries[segment.entryIndex]
      entry.textLayout() != null && !seen.add(entry.selectionId)
    }
  }

  private fun PageEntry.visibleTextRange(layout: StaticLayout): IntRange? {
    val lineRange = visibleLineRange(layout)
    val firstLine = lineRange.first
    val lastLine = lineRange.last
    val start = layout.getLineStart(firstLine)
    val endExclusive = layout.getLineEnd(lastLine)
    return if (start < endExclusive) start until endExclusive else null
  }

  private fun ReaderPage.selectedText(segments: List<SelectionSegment>): String =
    segments.joinToString(separator = "\n\n") { segment ->
      val layout = entries[segment.entryIndex].textLayout() ?: return@joinToString ""
      layout.text.substring(segment.range.first, segment.range.last + 1)
    }

  private fun selectionRects(
    layout: StaticLayout,
    range: IntRange,
    entry: PageEntry,
    horizontalMarginPx: Float,
    verticalMarginPx: Float,
  ): List<RectF> {
    val startLine = layout.getLineForOffset(range.first)
    val endLine = layout.getLineForOffset(range.last)
    val visibleLineRange = entry.visibleLineRange(layout)

    return (max(startLine, visibleLineRange.first)..min(endLine, visibleLineRange.last))
      .mapNotNull { line ->
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        val startOffset = max(range.first, lineStart)
        val endOffset = min(range.last + 1, lineEnd)
        if (startOffset >= endOffset) return@mapNotNull null

        val topOffset =
          when (entry) {
            is PageEntry.SplitBlock -> entry.firstLineTopPx
            else -> 0f
          }
        val bounds = layout.horizontalBounds(line, startOffset, endOffset, entry.textJustified)
        RectF(
          horizontalMarginPx + bounds.left + entry.drawOffsetXPx,
          verticalMarginPx + layout.getLineTop(line) - topOffset + entry.yOffsetPx,
          horizontalMarginPx + bounds.right + entry.drawOffsetXPx,
          verticalMarginPx + layout.getLineBottom(line) - topOffset + entry.yOffsetPx,
        )
      }
  }

  private fun PageEntry.visibleLineRange(layout: StaticLayout): IntRange =
    when (this) {
      is PageEntry.SplitBlock -> lineRange
      else -> 0 until layout.lineCount
    }

  private fun StaticLayout.horizontalBounds(
    line: Int,
    startOffset: Int,
    endOffset: Int,
    justified: Boolean,
  ): RectF {
    val lineStart = getLineStart(line)
    val lineEnd = getLineVisibleEnd(line).coerceAtLeast(lineStart)
    val start = startOffset.coerceIn(lineStart, lineEnd)
    val end = endOffset.coerceIn(lineStart, lineEnd)
    val text = text
    val lineLeft = getLineLeft(line)
    val measuredLineWidth = text.measureText(paint, lineStart, lineEnd)
    val extraSpace =
      if (justified) (getLineRight(line) - lineLeft - measuredLineWidth).coerceAtLeast(0f) else 0f
    val whitespaceCount = text.countWhitespace(lineStart, lineEnd)

    fun xFor(offset: Int): Float {
      val measuredPrefix = text.measureText(paint, lineStart, offset)
      val justifiedPrefix =
        if (whitespaceCount == 0) 0f
        else extraSpace * text.countWhitespace(lineStart, offset) / whitespaceCount
      return lineLeft + measuredPrefix + justifiedPrefix
    }

    val left = xFor(start)
    val right = xFor(end)
    return RectF(min(left, right), 0f, max(left, right), 0f)
  }

  private fun RectF.containsHorizontal(x: Float): Boolean = x >= left && x <= right

  private fun CharSequence.countWhitespace(start: Int, end: Int): Int =
    (start until end).count { index -> this[index].isWhitespace() }

  private fun CharSequence.hasLetterOrDigit(start: Int, end: Int): Boolean =
    (start until end).any { index -> this[index].isLetterOrDigit() }

  private fun CharSequence.cachedString(): String =
    if (this is String) this
    else synchronized(textStringCache) { textStringCache.getOrPut(this) { toString() } }

  private fun breakIterator(locale: Locale): BreakIterator =
    requireNotNull(breakIteratorCache.get()).getOrPut(locale) {
      BreakIterator.getWordInstance(locale)
    }

  private fun CharSequence.measureText(paint: TextPaint, start: Int, end: Int): Float {
    if (start >= end) return 0f
    if (this !is Spanned) return paint.measureText(this, start, end)

    var measuredWidth = 0f
    var segmentStart = start
    while (segmentStart < end) {
      val segmentEnd = nextSpanTransition(segmentStart, end, MetricAffectingSpan::class.java)
      val segmentPaint = TextPaint(paint)
      getSpans(segmentStart, segmentEnd, MetricAffectingSpan::class.java).forEach { span ->
        span.updateMeasureState(segmentPaint)
      }
      measuredWidth += segmentPaint.measureText(this, segmentStart, segmentEnd)
      segmentStart = segmentEnd
    }
    return measuredWidth
  }

  private fun List<RectF>.union(): RectF {
    val result = RectF(first())
    drop(1).forEach(result::union)
    return result
  }
}
