package com.charliesbot.kanshu.features.reader

import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.Length
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Either

// Kanshu's EPUB typography. Models the "layout-theirs, fonts-ours" split from
// docs/KINDLE_TYPOGRAPHY.md §5: publisher CSS shapes the book (publisherStyles = true) and
// user preferences override only the legibility layer — typeface, size, hyphenation. The
// `EpubDefaults` and `RsProperties` values below are therefore *fallbacks*, not overrides:
// publisher rules at equal specificity beat them, so they only take effect for properties the
// publisher didn't specify. Font-family is the one user pref that wins regardless because
// ReadiumCSS applies it with `!important`. Readium plumbs `fontFamily` only through
// `EpubPreferences`, never `EpubDefaults` — that's why the seed font lives in
// `initialPreferences` and not in `defaults`.
//
// Escape hatch: when ReadiumCSS + RsProperties no longer cover a rule we need (drop caps,
// blockquote ornament, vertical body padding in paginated mode, etc.), the documented path in
// Readium 3.1.2 is a Streamer-side `TransformingContainer` that rewrites spine HTML to inject
// a <link>. The navigator surface has no `<link>` injection hook. See docs/READIUM_API.md
// ("The Streamer escape hatch: TransformingContainer" and "What `body` actually gets") for the
// pattern and caveats.
@OptIn(ExperimentalReadiumApi::class)
internal object EpubTypography {

  val inter = FontFamily("Inter")
  val notoSerif = FontFamily("Noto Serif")

  val defaults =
    EpubDefaults(
      publisherStyles = true,
      columnCount = ColumnCount.ONE,
      fontSize = 1.0,
      lineHeight = 1.4,
      paragraphIndent = 1.5,
      paragraphSpacing = 0.0,
      textAlign = TextAlign.JUSTIFY,
      hyphens = true,
      ligatures = true,
    )

  val rsProperties =
    RsProperties(
      maxLineLength = Length.Rem(40.0),
      paraIndent = Length.Rem(1.5),
      baseLineHeight = Either.Right(1.4),
    )

  val initialPreferences = EpubPreferences(fontFamily = notoSerif, columnCount = ColumnCount.ONE)

  // Lazy so JVM unit tests that touch other members (e.g. ReaderViewModelTest exercising
  // EpubTypography.defaults) don't trigger Readium's addSource → android.net.Uri.encode path,
  // which throws "not mocked" off-device. Tests that need this val mock android.net.Uri.encode.
  val fragmentConfiguration: EpubNavigatorFragment.Configuration by lazy {
    EpubNavigatorFragment.Configuration().apply {
      servedAssets += "fonts/.*"
      readiumCssRsProperties = rsProperties

      addFontFamilyDeclaration(inter) {
        addFontFace {
          addSource("fonts/Inter-Variable.ttf", preload = true)
          setFontStyle(FontStyle.NORMAL)
          setFontWeight(100..900)
        }
        addFontFace {
          addSource("fonts/Inter-Italic-Variable.ttf")
          setFontStyle(FontStyle.ITALIC)
          setFontWeight(100..900)
        }
      }

      addFontFamilyDeclaration(notoSerif) {
        addFontFace {
          addSource("fonts/NotoSerif-Variable.ttf", preload = true)
          setFontStyle(FontStyle.NORMAL)
          setFontWeight(100..900)
        }
        addFontFace {
          addSource("fonts/NotoSerif-Italic-Variable.ttf")
          setFontStyle(FontStyle.ITALIC)
          setFontWeight(100..900)
        }
      }
    }
  }
}
