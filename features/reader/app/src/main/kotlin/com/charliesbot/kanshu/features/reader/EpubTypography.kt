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

// Kanshu's EPUB typography. Models Kindle's "layout from publisher, legibility from user" split
// per docs/KINDLE_TYPOGRAPHY.md: publisherStyles stays on so structural CSS (headings,
// blockquotes, list shape, indent rhythm) survives, while EpubPreferences drives the legibility
// primitives the user cares about (font family, size, line-height, alignment). Readium plumbs
// `fontFamily` only through `EpubPreferences`, never `EpubDefaults` — that's why the seed font
// lives here and not in defaults.
//
// Escape hatch: when ReadiumCSS + RsProperties can't constrain a fragile publisher pattern
// (`position: absolute`, oversized fixed widths), the documented path is a Streamer-level
// `TransformingContainer` that injects a normalization stylesheet — see docs/READIUM_API.md.
@OptIn(ExperimentalReadiumApi::class)
internal object EpubTypography {

  val inter = FontFamily("Inter")
  val notoSerif = FontFamily("Noto Serif")

  // Kindle's "Publisher Font ON for structure" mode: keep the publisher's headings, blockquotes,
  // list shape, and indent rhythm; override only the legibility primitives. ReadiumCSS-after.css
  // applies --USER__* with !important, so font/size/line-height/align still win over publisher
  // CSS for those specific properties even with publisherStyles = true. paragraphIndent and
  // paragraphSpacing are deliberately unset — those are the publisher's lane.
  val defaults =
    EpubDefaults(
      publisherStyles = true,
      columnCount = ColumnCount.ONE,
      fontSize = 1.0,
      lineHeight = 1.4,
      textAlign = TextAlign.JUSTIFY,
      hyphens = true,
      ligatures = true,
    )

  val rsProperties =
    RsProperties(maxLineLength = Length.Rem(40.0), baseLineHeight = Either.Right(1.4))

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
