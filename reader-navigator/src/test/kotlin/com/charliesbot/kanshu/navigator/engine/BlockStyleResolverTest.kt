package com.charliesbot.kanshu.navigator.engine

import android.graphics.Typeface
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.navigator.model.HeadingBlock
import com.charliesbot.kanshu.navigator.model.ParagraphBlock
import com.charliesbot.kanshu.navigator.model.TextLeaf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class BlockStyleResolverTest {
  @Test
  fun resolve_paragraph_paintUsesTheReaderTypeface() {
    val readerTypeface = Typeface.MONOSPACE
    val resolver = BlockStyleResolver(ReaderPreferences(), readerTypeface, density = 2f)

    val style = checkNotNull(resolver.resolve(ParagraphBlock(listOf(TextLeaf("x")))))

    assertSame(readerTypeface, style.paint.typeface)
  }

  @Test
  fun resolve_heading_paintDerivesBoldFromTheReaderTypeface() {
    val readerTypeface = Typeface.MONOSPACE
    val resolver = BlockStyleResolver(ReaderPreferences(), readerTypeface, density = 2f)

    val style =
      checkNotNull(resolver.resolve(HeadingBlock(level = 1, spans = listOf(TextLeaf("x")))))

    assertEquals(Typeface.create(readerTypeface, Typeface.BOLD), style.paint.typeface)
  }
}
