package com.charliesbot.kanshu.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KanshuJsBridgeTest {

  @Test
  fun testPageSettledEmitsCorrectEvent() {
    var receivedEvent: BridgeEvent? = null
    val bridge = KanshuJsBridge { event -> receivedEvent = event }

    bridge.onPageSettled(chapterLoadId = 12, pageIndex = 3, progressInSpine = 0.45f)

    assertTrue(receivedEvent is BridgeEvent.PageSettled)
    val pageSettled = receivedEvent as BridgeEvent.PageSettled
    assertEquals(12, pageSettled.chapterLoadId)
    assertEquals(3, pageSettled.pageIndex)
    assertEquals(0.45f, pageSettled.progressInSpine)
  }

  @Test
  fun testRepaginatedEmitsCorrectEvent() {
    var receivedEvent: BridgeEvent? = null
    val bridge = KanshuJsBridge { event -> receivedEvent = event }

    bridge.onRepaginated(
      chapterLoadId = 42,
      settingsRevision = 7,
      pageCount = 10,
      restoredPageIndex = 2,
      stalled = true,
    )

    assertTrue(receivedEvent is BridgeEvent.Repaginated)
    val repaginated = receivedEvent as BridgeEvent.Repaginated
    assertEquals(42, repaginated.chapterLoadId)
    assertEquals(7, repaginated.settingsRevision)
    assertEquals(10, repaginated.pageCount)
    assertEquals(2, repaginated.restoredPageIndex)
    assertTrue(repaginated.stalled)
  }
}
