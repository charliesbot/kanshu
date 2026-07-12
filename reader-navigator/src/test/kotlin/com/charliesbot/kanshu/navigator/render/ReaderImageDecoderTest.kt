package com.charliesbot.kanshu.navigator.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderImageDecoderTest {
  @Test
  fun computeInSampleSize_intrinsicSmallerThanTarget_isOne() {
    assertEquals(
      1,
      ReaderImageDecoder.computeInSampleSize(intrinsicWidthPx = 400, targetWidthPx = 500),
    )
  }

  @Test
  fun computeInSampleSize_intrinsicEqualToTarget_isOne() {
    assertEquals(
      1,
      ReaderImageDecoder.computeInSampleSize(intrinsicWidthPx = 500, targetWidthPx = 500),
    )
  }

  @Test
  fun computeInSampleSize_keepsDecodedWidthAtOrAboveTarget() {
    assertEquals(
      4,
      ReaderImageDecoder.computeInSampleSize(intrinsicWidthPx = 2000, targetWidthPx = 500),
    )
    assertEquals(
      2,
      ReaderImageDecoder.computeInSampleSize(intrinsicWidthPx = 2000, targetWidthPx = 600),
    )
  }

  @Test
  fun computeInSampleSize_zeroTarget_isOne() {
    assertEquals(
      1,
      ReaderImageDecoder.computeInSampleSize(intrinsicWidthPx = 2000, targetWidthPx = 0),
    )
  }

  @Test
  fun decode_validPng_returnsBitmap() {
    val bitmap =
      ReaderImageDecoder.decode(solidPngBytes(widthPx = 64, heightPx = 32), targetWidthPx = 64)

    assertNotNull(bitmap)
    assertEquals(64, bitmap!!.width)
    assertEquals(32, bitmap.height)
  }

  @Test
  fun decode_garbageBytes_returnsNull() {
    assertNull(ReaderImageDecoder.decode(ByteArray(16) { it.toByte() }, targetWidthPx = 100))
  }

  private fun solidPngBytes(widthPx: Int, heightPx: Int): ByteArray {
    val source = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    Canvas(source).drawColor(Color.DKGRAY)
    val output = ByteArrayOutputStream()
    source.compress(Bitmap.CompressFormat.PNG, 100, output)
    return output.toByteArray()
  }
}
