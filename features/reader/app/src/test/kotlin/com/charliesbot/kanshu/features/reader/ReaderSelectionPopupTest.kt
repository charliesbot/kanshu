package com.charliesbot.kanshu.features.reader

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ReaderSelectionPopupTest {
  @Test
  fun readerSelectionPopupPosition_clampsPopupInsideRightViewportEdge() {
    val position =
      readerSelectionPopupPosition(
        anchor = RectF(280f, 100f, 320f, 140f),
        viewportWidthPx = 300,
        viewportHeightPx = 500,
        popupWidthPx = 120,
        popupHeightPx = 40,
        marginPx = 8,
      )

    assertEquals(172, position.xPx)
    assertEquals(52, position.yPx)
  }

  @Test
  fun readerSelectionPopupPosition_clampsPopupInsideLeftViewportEdge() {
    val position =
      readerSelectionPopupPosition(
        anchor = RectF(2f, 20f, 40f, 44f),
        viewportWidthPx = 300,
        viewportHeightPx = 500,
        popupWidthPx = 120,
        popupHeightPx = 40,
        marginPx = 8,
      )

    assertEquals(8, position.xPx)
    assertEquals(52, position.yPx)
  }

  @Test
  fun readerSelectionPopupPosition_clampsPopupInsideBottomViewportEdge() {
    val position =
      readerSelectionPopupPosition(
        anchor = RectF(120f, 20f, 180f, 490f),
        viewportWidthPx = 300,
        viewportHeightPx = 500,
        popupWidthPx = 120,
        popupHeightPx = 40,
        marginPx = 8,
      )

    assertEquals(120, position.xPx)
    assertEquals(452, position.yPx)
  }

  @Test
  fun readerSelectionPopupPosition_usesPopupHeightToChooseBelowAnchor() {
    val position =
      readerSelectionPopupPosition(
        anchor = RectF(120f, 56f, 180f, 80f),
        viewportWidthPx = 300,
        viewportHeightPx = 500,
        popupWidthPx = 120,
        popupHeightPx = 80,
        marginPx = 8,
      )

    assertEquals(120, position.xPx)
    assertEquals(88, position.yPx)
  }
}
