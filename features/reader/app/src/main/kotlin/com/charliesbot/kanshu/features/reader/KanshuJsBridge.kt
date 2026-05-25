package com.charliesbot.kanshu.features.reader

import android.webkit.JavascriptInterface

sealed interface BridgeEvent {
  data class PageSettled(val chapterLoadId: Int, val pageIndex: Int, val progressInSpine: Float) :
    BridgeEvent

  data class Repaginated(
    val chapterLoadId: Int,
    val settingsRevision: Int,
    val pageCount: Int,
    val restoredPageIndex: Int,
    val stalled: Boolean,
  ) : BridgeEvent
}

class KanshuJsBridge(private val emit: (BridgeEvent) -> Unit) {
  @JavascriptInterface
  fun onPageSettled(chapterLoadId: Int, pageIndex: Int, progressInSpine: Float) {
    emit(BridgeEvent.PageSettled(chapterLoadId, pageIndex, progressInSpine))
  }

  @JavascriptInterface
  fun onRepaginated(
    chapterLoadId: Int,
    settingsRevision: Int,
    pageCount: Int,
    restoredPageIndex: Int,
    stalled: Boolean,
  ) {
    emit(
      BridgeEvent.Repaginated(
        chapterLoadId = chapterLoadId,
        settingsRevision = settingsRevision,
        pageCount = pageCount,
        restoredPageIndex = restoredPageIndex,
        stalled = stalled,
      )
    )
  }
}
