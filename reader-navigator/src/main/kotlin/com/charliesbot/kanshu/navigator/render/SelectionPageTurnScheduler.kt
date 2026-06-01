package com.charliesbot.kanshu.navigator.render

import android.os.Handler
import android.os.Looper

private const val SELECTION_PAGE_TURN_DELAY_MS = 900L
private const val SELECTION_PAGE_TURN_EDGE_DP = 64f

internal enum class SelectionPageTurnDirection {
  Previous,
  Next,
}

internal class SelectionPageTurnScheduler(
  private val handler: Handler = Handler(Looper.getMainLooper()),
  private val delayMs: Long = SELECTION_PAGE_TURN_DELAY_MS,
) {
  var isAwaitingPageTurn = false
    private set

  private var isScheduled = false
  private var scheduledDirection: SelectionPageTurnDirection? = null
  private var scheduledRunnable: Runnable? = null

  fun schedule(
    direction: SelectionPageTurnDirection,
    onPageTurn: (SelectionPageTurnDirection) -> Boolean,
  ) {
    if (isScheduled && scheduledDirection == direction) return

    cancelScheduledTurn()
    scheduledDirection = direction
    isScheduled = true
    scheduledRunnable = Runnable {
      isScheduled = false
      val turnDirection = scheduledDirection ?: return@Runnable
      scheduledDirection = null
      isAwaitingPageTurn = onPageTurn(turnDirection)
    }
    handler.postDelayed(requireNotNull(scheduledRunnable), delayMs)
  }

  fun cancel() {
    isAwaitingPageTurn = false
    scheduledDirection = null
    cancelScheduledTurn()
  }

  private fun cancelScheduledTurn() {
    isScheduled = false
    scheduledRunnable?.let(handler::removeCallbacks)
    scheduledRunnable = null
  }
}

internal fun Float.selectionPageTurnDirection(
  height: Int,
  density: Float,
): SelectionPageTurnDirection? {
  if (height <= 0) return null
  val edgeHeightPx = SELECTION_PAGE_TURN_EDGE_DP * density.coerceAtLeast(1f)
  return when {
    this <= edgeHeightPx -> SelectionPageTurnDirection.Previous
    this >= height - edgeHeightPx -> SelectionPageTurnDirection.Next
    else -> null
  }
}
