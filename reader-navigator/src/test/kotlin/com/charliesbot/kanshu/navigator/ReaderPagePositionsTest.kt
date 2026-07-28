package com.charliesbot.kanshu.navigator

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPagePositionsTest {

  private val positions =
    ReaderPagePositions(pageStartCharOffsets = listOf(0, 120, 260, 400), textStreamLength = 500)

  @Test
  fun pageIndexOf_resolvesOffsetToContainingPage() {
    assertEquals(0, positions.pageIndexOf(0))
    assertEquals(0, positions.pageIndexOf(119))
    assertEquals(1, positions.pageIndexOf(120))
    assertEquals(1, positions.pageIndexOf(259))
    assertEquals(3, positions.pageIndexOf(400))
  }

  @Test
  fun pageIndexOf_clampsOffsetsPastTheChapter() {
    // Re-pagination under a larger font yields fewer pages; a stored offset past the end must
    // still resume somewhere real rather than throwing or resetting to the chapter start.
    assertEquals(3, positions.pageIndexOf(99_999))
  }

  @Test
  fun pageIndexOf_negativeOffsetFallsBackToFirstPage() {
    assertEquals(0, positions.pageIndexOf(-1))
  }

  @Test
  fun pageIndexOf_emptyPositionsReturnFirstPage() {
    assertEquals(0, ReaderPagePositions.Empty.pageIndexOf(42))
  }

  @Test
  fun charOffsetOf_returnsPageStartAndZeroWhenUnknown() {
    assertEquals(260, positions.charOffsetOf(2))
    assertEquals(0, positions.charOffsetOf(99))
  }

  @Test
  fun progressInSpine_isTheFractionOfTheStreamRead() {
    assertEquals(0f, positions.progressInSpine(0), 0.0001f)
    assertEquals(0.24f, positions.progressInSpine(1), 0.0001f)
    assertEquals(0.8f, positions.progressInSpine(3), 0.0001f)
  }

  @Test
  fun progressInSpine_emptyStreamIsZeroRatherThanDivideByZero() {
    assertEquals(0f, ReaderPagePositions.Empty.progressInSpine(0), 0.0001f)
  }

  @Test
  fun pageIndexOf_prefersTheEarlierPageWhenPagesShareAnOffset() {
    // An image-only page consumes no characters, so it starts at the same offset as the text
    // page after it. Resuming onto the image page has to stay possible.
    val withImagePage =
      ReaderPagePositions(pageStartCharOffsets = listOf(0, 120, 120, 260), textStreamLength = 400)

    assertEquals(1, withImagePage.pageIndexOf(120))
    // An offset inside the text page still resolves past the shared boundary.
    assertEquals(2, withImagePage.pageIndexOf(200))
  }

  @Test
  fun roundTrip_offsetOfPageResolvesBackToThatPage() {
    positions.pageStartCharOffsets.indices.forEach { page ->
      assertEquals(page, positions.pageIndexOf(positions.charOffsetOf(page)))
    }
  }
}
