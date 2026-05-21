package com.charliesbot.kanshu.features.reader

import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderPreferences
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
// ReadiumCSS applies it with `!important` once `--USER__fontOverride: readium-font-on` is set —
// which the toolkit derives from the presence of a non-null fontFamily in `EpubPreferences`.
//
// Adding a new font is a three-step process documented in
// features/reader/app/src/main/assets/fonts/_HOW_TO_ADD_FONTS.txt: drop the .ttf, add a
// ReaderFont enum entry, and register the face in `fragmentConfiguration` plus the mapper
// below.
@OptIn(ExperimentalReadiumApi::class)
internal object EpubTypography {

  val bitter = FontFamily("Bitter")
  val libreBaskerville = FontFamily("Libre Baskerville")
  val literata = FontFamily("Literata")
  val openDyslexic = FontFamily("OpenDyslexic")

  // WORKAROUND for Readium PR #787 (CORS on @font-face). The kanshu* families resolve to the
  // `-Kanshu`-suffixed @font-face rules that core/data/.../fontworkaround/EpubFontInjector.kt
  // injects directly into chapter HTML. When the upstream fix lands, delete these four lines
  // and the wired-in `kanshu*` mappings in `readiumFamily` below — the unsuffixed FontFamily
  // values above will then route through Readium's own addFontFamilyDeclaration blocks.
  private val kanshuLiterata = FontFamily("Literata-Kanshu")
  private val kanshuBitter = FontFamily("Bitter-Kanshu")
  private val kanshuLibreBaskerville = FontFamily("Libre Baskerville-Kanshu")
  private val kanshuOpenDyslexic = FontFamily("OpenDyslexic-Kanshu")

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

  fun toEpubPreferences(prefs: ReaderPreferences): EpubPreferences =
    EpubPreferences(
      fontFamily = readiumFamily(prefs.font),
      fontSize = prefs.fontScale.toDouble(),
      columnCount = ColumnCount.ONE,
      pageMargins = prefs.margins.value,
      textAlign =
        when (prefs.alignment) {
          ReaderAlignment.Justify -> TextAlign.JUSTIFY
          ReaderAlignment.Left -> TextAlign.LEFT
        },
    )

  // WORKAROUND for Readium PR #787: returns the Kanshu-suffixed family so our injected
  // @font-face rule wins. Restore the commented unsuffixed branches when the upstream fix
  // releases.
  private fun readiumFamily(font: ReaderFont): FontFamily =
    when (font) {
      ReaderFont.Literata -> kanshuLiterata // -> literata
      ReaderFont.Bitter -> kanshuBitter // -> bitter
      ReaderFont.LibreBaskerville -> kanshuLibreBaskerville // -> libreBaskerville
      ReaderFont.OpenDyslexic -> kanshuOpenDyslexic // -> openDyslexic
    }

  // Lazy so JVM unit tests that touch other members (e.g. ReaderViewModelTest exercising
  // EpubTypography.defaults) don't trigger Readium's addSource → android.net.Uri.encode path,
  // which throws "not mocked" off-device. Tests that need this val mock android.net.Uri.encode.
  val fragmentConfiguration: EpubNavigatorFragment.Configuration by lazy {
    EpubNavigatorFragment.Configuration().apply {
      servedAssets += "fonts/.*"
      readiumCssRsProperties = rsProperties

      addFontFamilyDeclaration(literata) {
        addFontFace {
          addSource("fonts/Literata-VariableFont_opsz,wght.ttf", preload = true)
          setFontStyle(FontStyle.NORMAL)
          setFontWeight(100..900)
        }
        addFontFace {
          addSource("fonts/Literata-Italic-VariableFont_opsz,wght.ttf")
          setFontStyle(FontStyle.ITALIC)
          setFontWeight(100..900)
        }
      }

      addFontFamilyDeclaration(bitter) {
        addFontFace {
          addSource("fonts/Bitter-VariableFont_wght.ttf", preload = true)
          setFontStyle(FontStyle.NORMAL)
          setFontWeight(100..900)
        }
        addFontFace {
          addSource("fonts/Bitter-Italic-VariableFont_wght.ttf")
          setFontStyle(FontStyle.ITALIC)
          setFontWeight(100..900)
        }
      }

      addFontFamilyDeclaration(libreBaskerville) {
        addFontFace {
          addSource("fonts/LibreBaskerville-VariableFont_wght.ttf", preload = true)
          setFontStyle(FontStyle.NORMAL)
          setFontWeight(100..900)
        }
        addFontFace {
          addSource("fonts/LibreBaskerville-Italic-VariableFont_wght.ttf")
          setFontStyle(FontStyle.ITALIC)
          setFontWeight(100..900)
        }
      }

      // OpenDyslexic ships as four static faces rather than variable axes, so each weight/style
      // pairing gets its own addFontFace entry. Regular + bold both register the full 100..900
      // weight range so ReadiumCSS picks the right face when boldface CSS targets a heading or
      // <strong>.
      addFontFamilyDeclaration(openDyslexic) {
        addFontFace {
          addSource("fonts/OpenDyslexic-Regular.otf", preload = true)
          setFontStyle(FontStyle.NORMAL)
          setFontWeight(100..500)
        }
        addFontFace {
          addSource("fonts/OpenDyslexic-Italic.otf")
          setFontStyle(FontStyle.ITALIC)
          setFontWeight(100..500)
        }
        addFontFace {
          addSource("fonts/OpenDyslexic-Bold.otf")
          setFontStyle(FontStyle.NORMAL)
          setFontWeight(600..900)
        }
        addFontFace {
          addSource("fonts/OpenDyslexic-BoldItalic.otf")
          setFontStyle(FontStyle.ITALIC)
          setFontWeight(600..900)
        }
      }
    }
  }
}
