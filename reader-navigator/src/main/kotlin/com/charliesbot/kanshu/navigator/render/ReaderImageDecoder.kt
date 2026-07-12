package com.charliesbot.kanshu.navigator.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory

internal object ReaderImageDecoder {
  /**
   * Decodes image bytes subsampled to the drawn width. RGB_565 halves memory versus ARGB_8888; fine
   * for a B&W e-ink panel. Returns null for undecodable bytes.
   */
  fun decode(bytes: ByteArray, targetWidthPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options =
      BitmapFactory.Options().apply {
        inSampleSize = computeInSampleSize(bounds.outWidth, targetWidthPx)
        inPreferredConfig = Bitmap.Config.RGB_565
      }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
  }

  /**
   * Largest power of two that keeps the decoded width at or above the drawn width, so the canvas
   * only ever scales a sampled bitmap down.
   */
  fun computeInSampleSize(intrinsicWidthPx: Int, targetWidthPx: Int): Int {
    if (targetWidthPx <= 0 || intrinsicWidthPx <= targetWidthPx) return 1
    var sampleSize = 1
    while (intrinsicWidthPx / (sampleSize * 2) >= targetWidthPx) {
      sampleSize *= 2
    }
    return sampleSize
  }
}
