package com.charliesbot.kanshu.navigator.render

import android.view.MotionEvent

internal enum class ReaderPageTapZone {
  Previous,
  Center,
  Next,
}

internal fun MotionEvent.tapZone(viewWidth: Int): ReaderPageTapZone {
  val thirdWidth = viewWidth / 3f
  return when {
    x < thirdWidth -> ReaderPageTapZone.Previous
    x > thirdWidth * 2f -> ReaderPageTapZone.Next
    else -> ReaderPageTapZone.Center
  }
}
