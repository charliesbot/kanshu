# Readium Kotlin Toolkit — Reference for Kanshu

Practical reference for the slice of the Readium Kotlin Toolkit that Kanshu uses for EPUB rendering. Captures the verified surface area of v3.1.2 so we stop imagining APIs that don't exist (`addContentInjectable`, "custom hook for `<link>` injection", etc. — none of those are in this version).

## Source of truth

Pinned to `3.1.2`. Tag SHA `b254f10f39fd67fefbb34266a9f209b9d263862c`.

- Repo: <https://github.com/readium/kotlin-toolkit/tree/3.1.2>
- Navigator guide: <https://github.com/readium/kotlin-toolkit/blob/3.1.2/docs/guides/navigator/navigator.md>
- Preferences guide: <https://github.com/readium/kotlin-toolkit/blob/3.1.2/docs/guides/navigator/preferences.md>
- EPUB fonts guide: <https://github.com/readium/kotlin-toolkit/blob/3.1.2/docs/guides/navigator/epub-fonts.md>
- Open-publication guide: <https://github.com/readium/kotlin-toolkit/blob/3.1.2/docs/guides/open-publication.md>
- Migration guide (2.x → 3.x — explains why the API looks like it does today): <https://github.com/readium/kotlin-toolkit/blob/3.1.2/docs/migration-guide.md>
- ReadiumCSS variable reference (the keys behind `RsProperties.overrides`): <https://github.com/readium/readium-css/blob/master/docs/CSS19-api.md>

When this doc and behavior disagree, the source files above win. Verify against the pinned tag, not `main` — the API drifts.

## The two layers: Streamer and Navigator

Readium splits work into two halves and they live in different artifacts:

- **`readium-streamer`** opens an EPUB and produces a `Publication`. This is where `OpenBookUseCase` and `KavitaReaderSource` operate. Resource-level rewrites (e.g., inject a stylesheet into every spine HTML) happen here, _before_ rendering.
- **`readium-navigator`** takes a `Publication` and renders it inside a `Fragment` backed by a `WebView`. This is where `features/reader` operates. Settings, preferences, fonts, and decoration templates live here.

Anything you want to do that requires _new HTML in the spine_ (a `<link rel="stylesheet">`, an extra `<style>` block, fixing a busted `<head>`) is a Streamer job, not a Navigator job. See "CSS injection — the verdict" below.

## The two `Configuration` classes (the most common footgun)

There are two unrelated classes named `Configuration` in the EPUB navigator package. Confusing them produces silent no-ops:

| Class                                 | Path                                | What it carries                                                                                                                                                                                            |
| ------------------------------------- | ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `EpubNavigatorFactory.Configuration`  | `epub/EpubNavigatorFactory.kt:37`   | A single field: `defaults: EpubDefaults`. Passed to `EpubNavigatorFactory(publication, configuration)`.                                                                                                    |
| `EpubNavigatorFragment.Configuration` | `epub/EpubNavigatorFragment.kt:140` | Everything else: `servedAssets`, `readiumCssRsProperties`, font declarations, decoration templates, selection behavior, JS interfaces. Passed to `factory.createFragmentFactory(... configuration = ...)`. |

Font declarations, RS properties, asset patterns — all on the **fragment** Configuration, not the factory one. Kanshu's `EpubTypography.fragmentConfiguration` is correctly the fragment kind.

## Settings layering

Three classes drive what ends up in the WebView CSS. From bottom to top:

1. **`EpubDefaults`** — engine fallback values. App-controlled. Set via `EpubNavigatorFactory.Configuration(defaults = ...)`.
2. **`EpubPreferences`** — user-facing knobs. App-controlled. Set via `factory.createFragmentFactory(initialPreferences = ...)`, then mutated at runtime by a preferences editor.
3. **Hardcoded fallbacks** inside `EpubSettingsResolver` — Readium's last-resort defaults.

The resolver formula is `preferences.x ?: defaults.x ?: hardcoded` (see `EpubSettingsResolver.kt`). So every field always resolves to a concrete value at render time. `null` in `defaults` doesn't mean "no default" — it means "fall through to Readium's hardcoded fallback."

A few hardcoded fallbacks worth knowing:

| Setting           | Readium's hardcoded fallback |
| ----------------- | ---------------------------- |
| `publisherStyles` | `true`                       |
| `fontSize`        | `1.0`                        |
| `pageMargins`     | `1.0`                        |
| `scroll`          | `false`                      |
| `columnCount`     | `ColumnCount.AUTO`           |
| `spread`          | `Spread.NEVER`               |

If Kanshu's `EpubDefaults` doesn't set a field, the engine still ships _something_ — usually fine, occasionally surprising. Kanshu intentionally sets `publisherStyles = true` (matching Readium's upstream default) so the book's own CSS shapes layout, and our `EpubDefaults` / `RsProperties` values act as fallbacks for properties the publisher didn't specify rather than overrides. See `EpubTypography.kt` and `docs/KINDLE_TYPOGRAPHY.md` §5 for the model.

### `EpubDefaults` — every field

All nullable, all `@ExperimentalReadiumApi`. Source: `epub/EpubDefaults.kt:20-41`.

```
columnCount         ColumnCount?
fontSize            Double?
fontWeight          Double?
hyphens             Boolean?
imageFilter         ImageFilter?       // night-mode image treatment
language            Language?
letterSpacing       Double?
ligatures           Boolean?
lineHeight          Double?
pageMargins         Double?
paragraphIndent     Double?
paragraphSpacing    Double?
publisherStyles     Boolean?
readingProgression  ReadingProgression?
scroll              Boolean?
spread              Spread?
textAlign           TextAlign?
textNormalization   Boolean?
typeScale           Double?
wordSpacing         Double?
```

**Notable absences vs `EpubPreferences`**: no `backgroundColor`, `fontFamily`, `textColor`, `theme`, or `verticalText`. Those exist only as preferences — you can't pre-seed a font family in `EpubDefaults`, which is why `EpubTypography.initialPreferences` carries `fontFamily = notoSerif` instead.

### `EpubPreferences` — every field

All nullable, all `@ExperimentalReadiumApi`. Source: `epub/EpubPreferences.kt:51-77`.

Same as `EpubDefaults` plus: `backgroundColor: Color?`, `fontFamily: FontFamily?`, `textColor: Color?`, `theme: Theme?`, `verticalText: Boolean?`.

`init` validation enforces: `fontSize >= 0`, `fontWeight in 0.0..2.5`, `letterSpacing >= 0`, `pageMargins >= 0`, `paragraphSpacing >= 0`, `spread in {null, NEVER, ALWAYS}`, `typeScale >= 0`, `wordSpacing >= 0` (lines 79–88).

**Combining preferences**: `operator fun plus(other: EpubPreferences)` produces a right-wins-when-non-null merge — `other.foo ?: this.foo` per field (lines 91–118).

**Legacy migration**: `EpubPreferences.fromLegacyEpubSettings(context, sharedPreferencesName, fontFamilies)` (lines 132–241) reads the pre-2.3 `org.readium.r2.settings` SharedPreferences blob. Kanshu has never used the legacy store; this is dead code for us.

### `RsProperties` — the CSS reading-system layer

Separate object passed to `EpubNavigatorFragment.Configuration.readiumCssRsProperties`. Drives ReadiumCSS's `--RS__*` custom properties. Source: `epub/css/Properties.kt:234-346`. All nullable.

```
Pagination:        colWidth, colCount, colGap, pageGutter
Vertical rhythm:   flowSpacing, paraSpacing, paraIndent
Safeguards:        maxLineLength, maxMediaWidth, maxMediaHeight,
                   boxSizingMedia, boxSizingTable
Colors:            textColor, backgroundColor, selectionTextColor,
                   selectionBackgroundColor, linkColor, visitedColor,
                   primaryColor, secondaryColor
Typography:        typeScale, baseFontFamily, baseLineHeight
Latin font stacks: oldStyleTf, modernTf, sansTf, humanistTf, monospaceTf
Japanese stacks:   serifJa, sansSerifJa, serifJaV, sansSerifJaV
Unstyled defaults: compFontFamily, codeFontFamily

overrides: Map<String, String?>   ← escape hatch, see below
```

Field gotchas worth knowing before reaching for typed fields:

- **`pageGutter` is horizontal-only.** Despite the generic name, the bundled body rule (see "What `body` actually gets" below) is `padding: 0 var(--RS__pageGutter) !important;` — the shorthand zeros top/bottom. This field only controls left/right body padding. Confirmed by the docstring on `Properties.kt:187`: _"The horizontal page margins."_
- **`pageGutter` requires `Length.Absolute`.** `Length.Rem(2.0)` fails to compile — the constructor accepts only the `Absolute` arm of the sealed `Length` hierarchy (`Length.Px`, `Length.Pt`, `Length.Cm`, etc.). Surprising given other Length-typed fields take relative units.

The image-safeguard fields are the ones most relevant to fixing publisher overflow:

- `maxMediaWidth` → `--RS__maxMediaWidth`. ReadiumCSS default `100%`. Caps `img/svg/audio/video` width.
- `maxMediaHeight` → `--RS__maxMediaHeight`. ReadiumCSS default `100vh`. Caps height.
- `boxSizingMedia` → `--RS__boxSizingMedia`. ReadiumCSS default `border-box`.

So if you do nothing, ReadiumCSS already caps media at `100% × 100vh` with `border-box`. Setting these in `RsProperties` only matters if you want to _deviate_ from ReadiumCSS's defaults. This is non-obvious from the field names.

### What `body` actually gets (paginated mode)

Verified by reading the toolkit's bundled `readium/navigator/src/main/assets/readium/readium-css/ReadiumCSS-after.css` at the 3.1.2 tag — not upstream `readium/readium-css` master, which has drifted.

The unconditional body rule (lines 46–51 of the un-minified file):

```css
body {
  width: 100%;
  max-width: var(--RS__maxLineLength) !important;
  padding: 0 var(--RS__pageGutter) !important;
  margin: 0 auto !important;
  box-sizing: border-box;
}
```

The user override that fires when `--USER__pageMargins` is set (lines 174–175):

```css
:root[style*="--USER__pageMargins"] body {
  padding: 0 calc(var(--RS__pageGutter) * var(--USER__pageMargins)) !important;
}
```

Both rules use the `padding: 0 X` shorthand. **Top and bottom are literally hardcoded to `0`.** So `EpubPreferences.pageMargins = 2.0` doubles horizontal padding and does nothing vertical. `RsProperties.pageGutter` is similarly horizontal-only.

No `--RS__verticalPageGutter`, `--USER__verticalPageMargins`, `padding-block` variable, or anything equivalent exists anywhere across the three bundled stylesheets (`ReadiumCSS-before.css`, `-after.css`, `-default.css`). `--RS__scrollPaddingTop` / `--RS__scrollPaddingBottom` do exist but are gated on `:root:not(:--scroll-view)` matching — they only apply in scroll mode, never in paginated.

The "Vertical rhythm" RsProperties fields (`flowSpacing`, `paraSpacing`, `paraIndent`) only affect block-level elements inside body (`<p>`, `<h1-h6>`, `<blockquote>`, `<figure>`, etc.). They do not pad the body container itself and cannot move the last line of a packed page away from the WebView edge.

**If you need vertical reading margins in paginated mode**, your real options are:

- **Compose-side padding on the WebView host.** Shrinks the WebView's drawable area; affects every page uniformly, so the cover (or any image-only spread) is inset by the same amount you give text.
- **Custom CSS injected via `TransformingContainer`** with `body { padding-block: X !important; }`. Inject the `<link>` before `</body>` to beat ReadiumCSS-after.css on the cascade tiebreaker (see TransformingContainer caveats below). Has potential interactions with Readium's `column-fill: auto` + `height: 100vh` pagination math — unverified on real books, so test before shipping.

There is no third route exposed through the typed `EpubDefaults` / `EpubPreferences` / `RsProperties` API surface. Confirmed by exhaustive read of `Properties.kt`, `EpubSettingsResolver.kt`, and all three bundled CSS files at the 3.1.2 tag.

### The `overrides` map — what it actually does

`RsProperties.overrides: Map<String, String?>` looks like a generic CSS escape hatch but isn't. The implementation in `Properties.kt:24-35, 342-344`:

```kotlin
internal fun toCss(): String? = buildString {
    for ((key, value) in this@toCssProperties) {
        append("$key: $value !important;\n")
    }
}
```

`ReadiumCss.injectCssProperties()` (`epub/css/ReadiumCss.kt:176-184`) emits this as `style="..."` on the `<html>` element. So:

- Every entry becomes a single CSS property on `<html>`. Not a rule.
- The keys are intended to be Readium CSS variable names (`--RS__*`, `--USER__*`); the consumer does **not** validate keys, so anything you write will be set as an HTML inline-style declaration.
- **You cannot inject selectors, rules, `@font-face` blocks, or `@media` queries via `overrides`.** Only inline CSS properties on `<html>`.

The same caveat applies to `UserProperties.overrides`, which Kanshu can't construct directly anyway — `UserProperties` is built internally inside `EpubSettings.update(...)` from resolved settings. Your only handle on `--USER__*` is `EpubPreferences`.

## `EpubNavigatorFragment.Configuration` — every field

Source: `epub/EpubNavigatorFragment.kt:140-266`. The public secondary constructor (lines 214–231) is the one apps call.

| Field                            | Type                                      | Default                                              | Notes                                                                                                            |
| -------------------------------- | ----------------------------------------- | ---------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `servedAssets`                   | `List<String>`                            | `[]` (then `"readium/.*"` auto-appended at line 131) | Glob patterns for app `assets/` paths served at `https://readium/assets/`. `PatternMatcher.PATTERN_SIMPLE_GLOB`. |
| `readiumCssRsProperties`         | `RsProperties`                            | `RsProperties()`                                     | See above.                                                                                                       |
| `useReadiumCssFontSize`          | `Boolean`                                 | `true`                                               | `false` switches font sizing to `WebSettings.textZoom`. `@DelicateReadiumApi`.                                   |
| `decorationTemplates`            | `HtmlDecorationTemplates`                 | `defaultTemplates()`                                 | CSS for highlights / margin icons (Decorator API).                                                               |
| `disablePageTurnsWhileScrolling` | `Boolean`                                 | `false`                                              | Suppresses cross-resource swipes in scroll mode.                                                                 |
| `selectionActionModeCallback`    | `ActionMode.Callback?`                    | `null`                                               | Custom selection menu.                                                                                           |
| `shouldApplyInsetsPadding`       | `Boolean?`                                | `true`                                               | Display cutout handling.                                                                                         |
| `disableSelectionWhenProtected`  | `Boolean`                                 | `true` (only via public ctor)                        | DRM-aware. `@DelicateReadiumApi`.                                                                                |
| `fontFamilyDeclarations`         | `List<FontFamilyDeclaration>`             | `[]`                                                 | Internal var; mutate via `addFontFamilyDeclaration(...)`.                                                        |
| `javascriptInterfaces`           | `Map<String, JavascriptInterfaceFactory>` | `{}`                                                 | Internal var; mutate via `registerJavascriptInterface(...)`.                                                     |

DSL-style alternative: `Configuration { servedAssets += "fonts/.*"; addFontFamilyDeclaration(...) {} }` via the `invoke(builder)` companion at line 263.

**There is no `addContentInjectable`, `htmlInjectables`, `addHtmlInjection`, or any other arbitrary-injection method on this Configuration in 3.1.2.** Verified by full read of the file and `gh search code`. If you find yourself looking for it, jump to "CSS injection — the verdict."

## The font declaration API

Source: `epub/css/FontFamilyDeclaration.kt`.

```kotlin
addFontFamilyDeclaration(notoSerif) {
    addFontFace {
        addSource("fonts/NotoSerif-Variable.ttf", preload = true)
        setFontStyle(FontStyle.NORMAL)
        setFontWeight(100..900)          // variable-font range (Int range, 1..1000)
    }
    addFontFace {
        addSource("fonts/NotoSerif-Italic-Variable.ttf")
        setFontStyle(FontStyle.ITALIC)
        setFontWeight(100..900)
    }
}
```

This is the **only** mechanism on the navigator Configuration that emits _new HTML tags_ into spine `<head>` from the public API:

- For each `preload = true` source, a `<link rel="preload" as="font" crossorigin="" href="...">` is emitted before `<head>` (ReadiumCss.kt:122–149).
- For each declared face, a `@font-face { ... }` rule is emitted in a `<style>` block before `</head>`.

`addSource` paths must match a `servedAssets` glob — otherwise the asset URL `https://readium/assets/fonts/foo.ttf` 404s. That's why `EpubTypography.fragmentConfiguration` sets `servedAssets += "fonts/.*"` alongside the font declarations.

Variants of `addSource`:

- `addSource(path: String, preload: Boolean = false)` — path is decoded via `Url.fromDecodedPath`.
- `addSource(href: Url, preload: Boolean = false)` — when you already have a `Url`.

Weight options:

- `setFontWeight(FontWeight)` — fixed weight (enum `THIN..BLACK` = `100..900`).
- `setFontWeight(ClosedRange<Int>)` — variable fonts; range must lie within `1..1000`.

## `servedAssets` — what it does and what it doesn't

What it does: `WebViewServer` (`epub/WebViewServer.kt:39-175`) intercepts WebView requests whose host is `readium`. Two routes:

- `/publication/...` — serves publication resources via `publication.get(href)`, runs HTML through `Resource.injectHtml(...)` to add Readium's CSS + JS.
- `/assets/...` — only if the path matches a `servedAssets` glob, the request is delegated to `WebViewAssetLoader.AssetsPathHandler` which reads from the APK `assets/` folder.

So `servedAssets` is an **allowlist for app-side asset URL serving**. Nothing more.

What it doesn't do: it does not inject anything into spine HTML. Adding `"reader/.*"` to `servedAssets` makes `https://readium/assets/reader/kanshu.css` _fetchable_, but no spine HTML will ever reference it unless something else writes the `<link>` tag. The only auto-link Readium emits from `servedAssets` content is the font preload/`@font-face` chain described above, and that's hardcoded to font declarations.

## CSS injection — the verdict

Going through every candidate path in 3.1.2:

| Path                                                         | Can inject raw CSS rules into spine HTML?                                                    |
| ------------------------------------------------------------ | -------------------------------------------------------------------------------------------- |
| `EpubNavigatorFragment.Configuration.servedAssets`           | No — only serves files.                                                                      |
| `Configuration.readiumCssRsProperties`                       | No — sets `--RS__*` custom properties on `<html>` inline style. No rules.                    |
| `RsProperties.overrides` / `UserProperties.overrides`        | No — verbatim `key: value !important` into the same inline style.                            |
| `addFontFamilyDeclaration` / `addFontFace`                   | No — emits _only_ `<link rel="preload">` and `@font-face`, hardcoded to fonts.               |
| `decorationTemplates`                                        | Partial — adds CSS rules used only by Decorator-API-injected elements; not a spine-CSS hook. |
| `useReadiumCssFontSize`                                      | No — orthogonal.                                                                             |
| `registerJavascriptInterface` + runtime `evaluateJavascript` | Technically yes, but post-load, per-resource, flicker-prone. Not the right tool.             |
| **Streamer-level `TransformingContainer`**                   | **Yes. This is the only sanctioned path.**                                                   |

The shape of the 3.x API is intentional. From the migration guide (lines 976–986):

> The CSS, JavaScript and fonts injection in the Server was refactored... This is a breaking change... injection now happens directly by the Streamer.

The pre-3.x `server.loadCustomResource(...)` API is gone. There is no replacement on the Navigator surface for arbitrary CSS — it was moved to the Streamer's resource-transformer layer on purpose.

## The Streamer escape hatch: `TransformingContainer`

When you actually need to inject a `<link>` or `<style>` into spine HTML, this is the documented mechanism. Two helpers in `readium-shared`:

- **`TransformingContainer`** — `shared/src/main/java/org/readium/r2/shared/util/resource/TransformingContainer.kt`. The class doc comment explicitly lists "inject CSS or JavaScript" as an intended use case. Constructor takes the original `Container<Resource>` and a list of `EntryTransformer = (Url, Resource) -> Resource` functions; transformers fold over each resource in order.
- **`TransformingResource`** / `Resource.map { bytes -> ... }` — per-resource byte rewriting. Used internally by Readium itself for `injectHtml`.

Integration point: `PublicationOpener.open(... onCreatePublication = { ... })` (`streamer/src/main/java/org/readium/r2/streamer/PublicationOpener.kt:74-111`). The lambda runs on a `Publication.Builder` whose `container: Container<Resource>` is settable.

Canonical pattern:

```kotlin
publicationOpener.open(asset, allowUserInteraction = true) {
    container = TransformingContainer(container) { url, resource ->
        val path = url.path.orEmpty()
        if (path.endsWith(".xhtml") || path.endsWith(".html")) {
            resource.map { bytes ->
                val html = bytes.decodeToString()
                val patched = html.replace(
                    "</head>",
                    """<link rel="stylesheet" href="https://readium/assets/reader/kanshu.css"/></head>"""
                )
                Try.success(patched.encodeToByteArray())
            }
        } else {
            resource
        }
    }
}
```

For the URL above to resolve, the asset still has to be in `servedAssets` on the navigator side (`servedAssets += "reader/.*"`). Streamer rewrites the spine bytes; Navigator serves the referenced file. **Both halves are required.**

Caveats:

- `TransformingResource` loads the full resource into memory (own doc comment, line 25–27). Fine for spine HTML, not for video.
- Readium's own `<link>` to `ReadiumCSS-after.css` is injected _after_ `</head>` closes, so it beats your `<link>` on `!important` ties when specificity matches. Two ways to win the cascade: higher specificity (e.g., `html body` beats bare `body`), or inject your `<link>` before `</body>` so it sits later in document order than Readium's.
- **XHTML strict parsing.** Spine HTML can be parsed strictly as XHTML — a duplicate `style="..."` attribute on the same element is a fatal parse error (`Attribute style redefined` at the offending line; the WebView renders only the parser-error stub, no content). If you string-rewrite a tag to add a `style` attribute, strip any existing `style` first or the page fails to render entirely.
- **Publisher inline `style="..."` beats external CSS.** Inline styles win over external rules at equal `!important`. The only counter is more-inline (HTML mutation to replace or merge the publisher's `style` attribute), which collides with the XHTML caveat above. Worth flagging because for cover XHTML this rarely matters — most publishers don't inline-style covers — but for text spines that hand-style specific elements you may not be able to override them externally.

We haven't implemented this in Kanshu yet. Documented here so we don't reinvent it or assume it's impossible.

## Listeners and input

3.x renamed and slimmed the listener hierarchy. Verified entry points in 3.1.2:

- `EpubNavigatorFragment.Listener` — empty marker interface combining `OverflowableNavigator.Listener` and `HyperlinkNavigator.Listener`. The latter exposes `shouldFollowInternalLink(link, context): Boolean` and `onExternalLinkActivated(url: AbsoluteUrl)`. Kanshu uses a no-op for the latter and inherits the rest.
- `EpubNavigatorFragment.PaginationListener` — optional, two methods: `onPageChanged(pageIndex, totalPages, locator)` and `onPageLoaded()`.
- `InputListener` (`input/InputListener.kt`) — `onTap`, `onDrag`, `onKey`, all return `Boolean` (false = pass through). Multiple listeners compose via internal `CompositeInputListener`; first to return `true` consumes.
- `VisualNavigator.addInputListener(listener)` / `removeInputListener(listener)` — still the entry point for attaching custom input listeners. Used by Kanshu to attach `DirectionalNavigationAdapter`.

### `DirectionalNavigationAdapter` parameters

Constructor knobs worth knowing (`util/DirectionalNavigationAdapter.kt`):

- `tapEdges` — default `{TapEdge.Horizontal}`. Add `TapEdge.Vertical` to enable top/bottom zones.
- `handleTapsWhileScrolling` — default `false`.
- `minimumHorizontalEdgeSize` — default `80.0` (dp).
- `horizontalEdgeThresholdPercent` — default `0.3`.
- `animatedTransition` — default `false`. Already what we want for e-ink; leave it.

The adapter is an `InputListener`, not a `Listener.onTap` callback. The old `VisualNavigator.Listener.onTap` / `onDrag` are deprecated as of 3.x — use `InputListener` everywhere.

## TOC navigation: no upstream API for "current chapter from locator"

3.1.2 ships `Publication.locatorFromLink(link)` — TOC → Locator (used to navigate _to_ a chapter once the user has picked one) — but no inverse. Specifically, **none** of these exist:

- `Publication.findCurrentTocEntry(locator)`
- `Navigator.nextChapter()` / `previousChapter()`
- `Publication.linkWithHref(href)` searching the TOC (it only walks `readingOrder` / `resources` / `links`).

The TestApp's outline screen confirms this: it only handles the easy direction (`onLinkSelected` → `locatorFromLink` → `navigator.go`). It never asks "what TOC entry am I in?"

The canonical pattern, mirrored from `Manifest.linkWithHref`, is to walk the TOC ourselves and compare normalized URLs on both sides:

- Resolve every TOC `Link.href` to a `Url` via `link.url()`.
- Strip fragment + query, then `normalize()`.
- Do the same to `locator.href.normalizeForMatch()`.
- Equality match. Pick `indexOfLast` so a child TOC entry beats its parent when both point at the same resource.

For locators that fall on spine resources that aren't TOC anchors (front-matter, mid-chapter sub-resources), a spine fallback is required: find the locator's index in `publication.readingOrder` and pick the latest TOC entry whose spine index is at or before it. The cover and any pre-TOC pages need a final edge case (no preceding TOC entry → `next = TOC[0]`).

Implementation lives in `features/reader/.../TocIndex.kt`; see `TocIndexTest` for branch coverage.

## Migration history that shapes the current API

Worth knowing because most online tutorials predate it and contain dead patterns:

1. **The local HTTP server is gone.** Pre-3.x Readium ran a NanoHTTPD server on a local port and you injected assets via `server.loadCustomResource(...)`. In 3.x, the WebView talks to a synthetic `https://readium/` host via `shouldInterceptRequest`. Any tutorial that references `Server.loadCustomResource` is 2.x and the API no longer exists.
2. **Preferences replaced settings.** The 2.x `userSettingsUIPreset` / `UserSettings.kt` flow is dead. `EpubPreferences` is now the only knob. `EpubPreferences.fromLegacyEpubSettings(...)` exists solely to migrate the legacy SharedPreferences blob.
3. **`EdgeTapNavigation` → `DirectionalNavigationAdapter`.** The 2.x `EdgeTapNavigation` is deprecated; 3.x ships `DirectionalNavigationAdapter` as an `InputListener` attached via `addInputListener`.
4. **`VisualNavigator.Listener.onTap` / `onDrag` deprecated.** Use `InputListener` implementations. First to return `true` consumes.
5. **Footnote popup removed.** Default 3.x behavior jumps to the footnote target. To restore the popup, override `HyperlinkNavigator.Listener.shouldFollowInternalLink(link, context)` and inspect `FootnoteContext`. Not on Kanshu's roadmap; documented in case.

## Kanshu-specific footguns (already paid for)

- **`addSource(path)` routes through `android.net.Uri.encode`.** Off-device JVM tests fail with "Uri not mocked" if they materialize `EpubTypography.fragmentConfiguration`. That's why `fragmentConfiguration` is `by lazy` and `EpubTypographyTest` doesn't touch it. If we ever bring in Robolectric, we can fold the `servedAssets` assertion back in.
- **`supportFragmentManager.fragmentFactory` is global.** Setting it from `EpubNavigatorHost` is fine for our single-reader-at-a-time UX but would clobber under concurrent fragment users. Don't add a second fragment-factory-using feature without revisiting this.
- **`Publication.close()` is blocking I/O.** Called fire-and-forget on `Dispatchers.IO` from `ReaderViewModel.onCleared` because `viewModelScope` is already cancelled at that point.
- **Process-death stub fragment.** `MainActivity` installs a dummy fragment factory on process restoration. The host has to remove that stub fragment before adding the real navigator, otherwise `FragmentManager` merges them. See `EpubNavigatorHost` `DisposableEffect`.

## Where the work lives in Kanshu

- `features/reader/app/.../EpubTypography.kt` — defaults, preferences, RsProperties, font declarations.
- `features/reader/app/.../EpubNavigatorHost.kt` — fragment lifecycle, `DirectionalNavigationAdapter` wiring.
- `features/reader/app/.../ReaderViewModel.kt` — `EpubNavigatorFactory` construction, `Publication` ownership.
- `core/.../reader/KavitaReaderSource.kt` — would be the integration point if/when we add a `TransformingContainer` (because that's where the `Publication` is opened).

When extending: figure out which side of the Streamer/Navigator line your change lives on _before_ writing code. Navigator surface = settings, prefs, fonts, decorations. Streamer surface = resource rewrites, including any CSS injection.
