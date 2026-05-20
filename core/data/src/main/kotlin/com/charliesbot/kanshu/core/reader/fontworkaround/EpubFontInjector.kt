package com.charliesbot.kanshu.core.reader.fontworkaround

import android.content.Context
import android.util.Base64
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.resource.TransformingContainer
import org.readium.r2.shared.util.resource.map

// WORKAROUND for Readium kotlin-toolkit issue: WebViewServer serves publication HTML from host
// `readium_package` and bundled fonts from `readium_assets`. Browser CORS rejects every
// cross-origin @font-face fetch because androidx.webkit.WebViewAssetLoader.AssetsPathHandler
// emits responses without an Access-Control-Allow-Origin header. PR #787 upstream
// (https://github.com/readium/kotlin-toolkit/pull/787) adds the header — not yet merged at the
// time of writing. The CORS failure is silent: browsers fall back to system fonts, so most apps
// using addFontFamilyDeclaration never realize their custom fonts aren't actually loading.
//
// We sidestep the issue by injecting our own @font-face declarations directly into each chapter's
// HTML head, using `data:` URIs for the font binary. Data URIs have no origin, so CORS never
// kicks in. To avoid colliding with Readium's still-emitted-but-broken @font-face declarations
// for the same families, our rules use a `-Kanshu` suffix on the family name. EpubTypography's
// `readiumFamily` mapping submits the same suffixed names in EpubPreferences so the cascade
// lines up.
//
// HOW TO REMOVE THIS WORKAROUND when Readium ships the fix:
//   1. Delete this file.
//   2. Delete the `wrapWithKanshuFontInjection(...)` call in KavitaReaderSource.
//   3. In EpubTypography.readiumFamily, drop the `-Kanshu` suffix from each returned FontFamily
//      name (or just delete the kanshu* constants and use the unsuffixed ones).
// That's all — Readium's own `addFontFamilyDeclaration` blocks in EpubTypography then take over.
internal object KanshuFontWorkaround {

  // Family name written into both the @font-face rule and EpubPreferences. Kept in sync between
  // this file and EpubTypography.readiumFamily; if the suffix changes, change it in both places.
  const val FAMILY_SUFFIX = "-Kanshu"

  private val faces: List<FontFace> =
    listOf(
      FontFace(
        family = "Literata$FAMILY_SUFFIX",
        assetPath = "fonts/Literata-VariableFont_opsz,wght.ttf",
        style = "normal",
        weight = "100 900",
      ),
      FontFace(
        family = "Literata$FAMILY_SUFFIX",
        assetPath = "fonts/Literata-Italic-VariableFont_opsz,wght.ttf",
        style = "italic",
        weight = "100 900",
      ),
      FontFace(
        family = "Bitter$FAMILY_SUFFIX",
        assetPath = "fonts/Bitter-VariableFont_wght.ttf",
        style = "normal",
        weight = "100 900",
      ),
      FontFace(
        family = "Bitter$FAMILY_SUFFIX",
        assetPath = "fonts/Bitter-Italic-VariableFont_wght.ttf",
        style = "italic",
        weight = "100 900",
      ),
      FontFace(
        family = "Libre Baskerville$FAMILY_SUFFIX",
        assetPath = "fonts/LibreBaskerville-VariableFont_wght.ttf",
        style = "normal",
        weight = "100 900",
      ),
      FontFace(
        family = "Libre Baskerville$FAMILY_SUFFIX",
        assetPath = "fonts/LibreBaskerville-Italic-VariableFont_wght.ttf",
        style = "italic",
        weight = "100 900",
      ),
      FontFace(
        family = "OpenDyslexic$FAMILY_SUFFIX",
        assetPath = "fonts/OpenDyslexic-Regular.otf",
        style = "normal",
        weight = "100 500",
      ),
      FontFace(
        family = "OpenDyslexic$FAMILY_SUFFIX",
        assetPath = "fonts/OpenDyslexic-Italic.otf",
        style = "italic",
        weight = "100 500",
      ),
      FontFace(
        family = "OpenDyslexic$FAMILY_SUFFIX",
        assetPath = "fonts/OpenDyslexic-Bold.otf",
        style = "normal",
        weight = "600 900",
      ),
      FontFace(
        family = "OpenDyslexic$FAMILY_SUFFIX",
        assetPath = "fonts/OpenDyslexic-BoldItalic.otf",
        style = "italic",
        weight = "600 900",
      ),
    )

  private data class FontFace(
    val family: String,
    val assetPath: String,
    val style: String,
    val weight: String,
  )

  private val headRegex = Regex("</head>", RegexOption.IGNORE_CASE)

  // The full <style> block including all @font-face rules. Computed lazily on first use and
  // cached for the lifetime of the process. Loading + base64-encoding all bundled faces totals
  // ~2.4 MB of String in memory — acceptable on a tablet, and only paid once per process.
  @Volatile private var cachedStyleBlock: String? = null

  private fun styleBlock(context: Context): String {
    cachedStyleBlock?.let {
      return it
    }
    synchronized(this) {
      cachedStyleBlock?.let {
        return it
      }
      val rules =
        faces.joinToString("\n") { face ->
          val bytes = context.assets.open(face.assetPath).use { it.readBytes() }
          val mediaType = if (face.assetPath.endsWith(".otf")) "font/otf" else "font/ttf"
          val format = if (face.assetPath.endsWith(".otf")) "opentype" else "truetype"
          val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
          """
          @font-face {
            font-family: "${face.family}";
            src: url("data:$mediaType;base64,$base64") format("$format");
            font-style: ${face.style};
            font-weight: ${face.weight};
            font-display: block;
          }
          """
            .trimIndent()
        }
      // ReadiumCSS only forces `font-family: inherit !important` on body/dd/div/dt/li/p
      // (see ReadiumCSS-after.css), so a publisher's `h1 { font-family: ... }` rule wins
      // for headings and the user-selected font silently doesn't apply to titles. We extend
      // the inherit list to the heading + common structural elements that publishers
      // typically style. `pre` and `code` are intentionally excluded so monospace stays
      // monospace. Reads `var(--USER__fontFamily)` so it works for whichever font the user
      // picks — no per-font duplication.
      val headingOverride =
        """
        :root[style*="--USER__fontFamily"] h1,
        :root[style*="--USER__fontFamily"] h2,
        :root[style*="--USER__fontFamily"] h3,
        :root[style*="--USER__fontFamily"] h4,
        :root[style*="--USER__fontFamily"] h5,
        :root[style*="--USER__fontFamily"] h6,
        :root[style*="--USER__fontFamily"] header,
        :root[style*="--USER__fontFamily"] footer,
        :root[style*="--USER__fontFamily"] blockquote,
        :root[style*="--USER__fontFamily"] figcaption {
          font-family: inherit !important;
        }
        """
          .trimIndent()
      val built = "<style id=\"kanshu-fonts\">\n$rules\n$headingOverride\n</style>"
      cachedStyleBlock = built
      return built
    }
  }

  // Returns the same `builder` with its container swapped for a TransformingContainer that
  // rewrites each HTML / XHTML resource to add our @font-face <style> block immediately before
  // the closing </head>. Non-HTML resources pass through unchanged.
  fun wrap(builder: Publication.Builder, context: Context): Publication.Builder {
    builder.container =
      TransformingContainer(builder.container) { url, resource ->
        val path = url.path?.lowercase() ?: return@TransformingContainer resource
        if (!path.endsWith(".xhtml") && !path.endsWith(".html") && !path.endsWith(".htm")) {
          return@TransformingContainer resource
        }
        resource.map { bytes ->
          val html = bytes.decodeToString()
          // Use the lambda form of Regex.replace so `$` in the style block isn't interpreted as
          // a backreference. (The current style block has no `$`, but base64 of a future font
          // could in theory contain one in a fuzzed input.)
          val patched = headRegex.replace(html) { "${styleBlock(context)}</head>" }.toByteArray()
          Try.success(patched)
        }
      }
    return builder
  }
}

fun wrapWithKanshuFontInjection(
  builder: Publication.Builder,
  context: Context,
): Publication.Builder = KanshuFontWorkaround.wrap(builder, context)
