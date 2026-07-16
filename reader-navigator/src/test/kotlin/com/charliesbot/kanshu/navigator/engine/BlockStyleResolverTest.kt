package com.charliesbot.kanshu.navigator.engine

import android.graphics.Typeface
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.navigator.model.BlockAlignment
import com.charliesbot.kanshu.navigator.model.BlockSpacing
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
  fun resolve_paragraph_defaultPreferencesUseIndentConventionNotGaps() {
    val resolver = BlockStyleResolver(ReaderPreferences(), Typeface.MONOSPACE, density = 2f)

    val style = checkNotNull(resolver.resolve(ParagraphBlock(listOf(TextLeaf("x")))))

    // Kindle's unstyled-book convention: paragraphs separate by first-line indent, not gaps.
    assertEquals(0f, style.marginTopPx, 0.01f)
    assertEquals(0f, style.marginBottomPx, 0.01f)
    assertEquals(1.5f * FONT_SIZE_PX, style.firstLineIndentPx, 0.01f)
  }

  @Test
  fun resolve_paragraph_publisherSpacingOverridesTheConvention() {
    val resolver = BlockStyleResolver(ReaderPreferences(), Typeface.MONOSPACE, density = 2f)

    val style =
      checkNotNull(
        resolver.resolve(
          ParagraphBlock(
            listOf(TextLeaf("x")),
            spacing = BlockSpacing(marginTopEm = 1f, marginBottomEm = 0.5f, textIndentEm = 0f),
          )
        )
      )

    assertEquals(FONT_SIZE_PX, style.marginTopPx, 0.01f)
    assertEquals(0.5f * FONT_SIZE_PX, style.marginBottomPx, 0.01f)
    assertEquals(0f, style.firstLineIndentPx, 0.01f)
  }

  @Test
  fun resolve_paragraph_publisherMarginsSuppressTheDefaultIndent() {
    val resolver = BlockStyleResolver(ReaderPreferences(), Typeface.MONOSPACE, density = 2f)

    val style =
      checkNotNull(
        resolver.resolve(
          ParagraphBlock(listOf(TextLeaf("x")), spacing = BlockSpacing(marginTopEm = 1f))
        )
      )

    // The publisher declared spacing for this block; the indent convention no longer applies.
    assertEquals(0f, style.firstLineIndentPx, 0.01f)
  }

  @Test
  fun resolve_paragraph_userSpacingIsAdditiveOverPublisherMargins() {
    val resolver =
      BlockStyleResolver(
        ReaderPreferences(paragraphSpacing = 0.5f),
        Typeface.MONOSPACE,
        density = 2f,
      )

    val style =
      checkNotNull(
        resolver.resolve(
          ParagraphBlock(listOf(TextLeaf("x")), spacing = BlockSpacing(marginBottomEm = 1f))
        )
      )

    assertEquals(1.5f * FONT_SIZE_PX, style.marginBottomPx, 0.01f)
  }

  @Test
  fun resolve_centeredParagraph_getsNoDefaultIndent() {
    val resolver = BlockStyleResolver(ReaderPreferences(), Typeface.MONOSPACE, density = 2f)

    val style =
      checkNotNull(
        resolver.resolve(
          ParagraphBlock(listOf(TextLeaf("* * *")), alignment = BlockAlignment.Center)
        )
      )

    assertEquals(0f, style.firstLineIndentPx, 0.01f)
  }

  @Test
  fun resolve_paragraph_horizontalMarginsMapToInsets() {
    val resolver = BlockStyleResolver(ReaderPreferences(), Typeface.MONOSPACE, density = 2f)

    val style =
      checkNotNull(
        resolver.resolve(
          ParagraphBlock(
            listOf(TextLeaf("x")),
            spacing = BlockSpacing(marginStartEm = 1f, marginEndEm = 2f),
          )
        )
      )

    assertEquals(FONT_SIZE_PX, style.indentPx, 0.01f)
    assertEquals(2f * FONT_SIZE_PX, style.endInsetPx, 0.01f)
  }

  @Test
  fun resolve_heading_publisherMarginsOverrideHeadingDefaults() {
    val resolver = BlockStyleResolver(ReaderPreferences(), Typeface.MONOSPACE, density = 2f)

    val style =
      checkNotNull(
        resolver.resolve(
          HeadingBlock(
            level = 1,
            spans = listOf(TextLeaf("x")),
            spacing = BlockSpacing(marginTopEm = 0.5f, marginBottomEm = 0.25f),
          )
        )
      )

    assertEquals(0.5f * FONT_SIZE_PX, style.marginTopPx, 0.01f)
    assertEquals(0.25f * FONT_SIZE_PX, style.marginBottomPx, 0.01f)
  }

  @Test
  fun resolve_heading_paintDerivesBoldFromTheReaderTypeface() {
    val readerTypeface = Typeface.MONOSPACE
    val resolver = BlockStyleResolver(ReaderPreferences(), readerTypeface, density = 2f)

    val style =
      checkNotNull(resolver.resolve(HeadingBlock(level = 1, spans = listOf(TextLeaf("x")))))

    assertEquals(Typeface.create(readerTypeface, Typeface.BOLD), style.paint.typeface)
  }

  private companion object {
    // Body font at default scale and the density used across these tests: 18sp * 1.0 * 2.
    const val FONT_SIZE_PX = 36f
  }
}
