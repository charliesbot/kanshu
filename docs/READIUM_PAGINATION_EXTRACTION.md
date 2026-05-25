# Readium Pagination Extraction

This doc is the implementation brief for rebuilding Kanshu's custom WebView reader. It answers two questions from source code, not memory:

- How does Readium apply and construct EPUB styles?
- How does Readium paginate and move through reflowable content?

## Source Discipline

Every renderer decision that copies or rejects Readium behavior must cite the exact Readium source file inspected at Kanshu's pinned version. Summaries, old docs, and training-data memory are not enough.

Kanshu currently pins `org.readium.kotlin-toolkit` to `3.2.0` in `gradle/libs.versions.toml`. The inspected Readium tag is `3.2.0`, which resolves to commit `fe4c32b97f4745971facfbdfbde31520553c770e` in a detached checkout.

Primary source root: <https://github.com/readium/kotlin-toolkit/tree/3.2.0>

## Style Construction In Readium

Readium does not rely on the original EPUB document alone. When a WebView requests a publication resource, `WebViewServer.shouldInterceptRequest(...)` routes publication URLs through `servePublicationResourceWithHref(...)`. If the publication conforms to EPUB and the media type is HTML, it wraps the resource with `resource.injectHtml(...)` before serving it to WebView. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/WebViewServer.kt#L105-L194>

`HtmlInjector.kt` is the HTML entry point. For reflowable EPUB resources, it calls `css.injectHtml(content)` first, then queues `readium/scripts/readium-reflowable.js`, and finally inserts the queued scripts before `</head>`. Fixed layout skips `ReadiumCss.injectHtml(...)` and loads `readium-fixed.js` instead. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/HtmlInjector.kt#L29-L87>

`ReadiumCss.injectHtml(...)` performs four mutations: stylesheet/font injection, inline CSS property injection on `<html>`, `dir` injection, and language injection. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/css/ReadiumCss.kt#L28-L43>

The stylesheet order is deliberate. Readium inserts font preload links and `ReadiumCSS-before.css` immediately after the opening `<head>`, inserts `ReadiumCSS-default.css` only when the content has no author styles, then inserts `ReadiumCSS-after.css` before `</head>`. Font `@font-face` declarations are also inserted near the end of `<head>`. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/css/ReadiumCss.kt#L45-L99>

Readium has a specific pagination repair rule for publisher CSS that sets `overflow-x: hidden`. It injects an inline `<style>` that forces `:root` and `body` overflow back to `visible !important`. This exists because hidden overflow can break column pagination. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/css/ReadiumCss.kt#L64-L74>

Settings become CSS variables, not full CSS rules. `ReadiumCss.injectCssProperties(...)` concatenates reading-system and user properties, escapes quotes, and inserts them as a `style="..."` attribute on `<html>` before the first layout pass. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/css/ReadiumCss.kt#L170-L184>

At runtime, changed properties are pushed to already-loaded resources by running `readium.setCSSProperties({...})` in each loaded WebView. New resources get the current property set again after load. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/EpubNavigatorViewModel.kt#L117-L162>

The JS side applies those updates by setting each CSS property on `document.documentElement.style` with the `important` priority. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/assets/_scripts/src/utils.js#L351-L374>

## Settings To CSS Variables

`EpubSettingsResolver` resolves app preferences and defaults into concrete `EpubSettings`. The relevant fallback chain is `preferences.x ?: defaults.x ?: hardcoded`. For pagination, `scroll` defaults to `false`, `columnCount` defaults to `AUTO`, `pageMargins` defaults to `1.0`, `publisherStyles` defaults to `true`, and `spread` defaults to `NEVER`. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/EpubSettingsResolver.kt#L23-L65>

Readium disables paginated CSS columns for vertical text by forcing `scroll = true` when `verticalText` is resolved. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/EpubSettingsResolver.kt#L31-L37>

`ReadiumCss.update(...)` maps resolved settings to `UserProperties`. Important mappings for Kanshu are `scroll -> --USER__view`, `columnCount -> --USER__colCount`, `pageMargins -> --USER__pageMargins`, theme/image settings, font override/family/size, and advanced typography settings such as alignment, line height, paragraph spacing, indent, word spacing, and letter spacing. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/EpubSettings.kt#L66-L143>

`UserProperties.toCssProperties()` emits the actual `--USER__*` variables. `pageMargins` is specifically documented as a factor applied to horizontal margins, not vertical margins. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/css/Properties.kt#L38-L177>

`RsProperties` exposes reading-system variables for column width, column count, column gap, horizontal page gutter, vertical rhythm, media safeguards, colors, type scale, base font family, and default font stacks. The doc comment calls `pageGutter` horizontal page margins. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/css/Properties.kt#L180-L346>

Both `UserProperties` and `RsProperties` use `Properties.toCss()`, which emits `key: value !important;` declarations. This is why Readium's variable layer is strong in the cascade, but it is still only inline properties on `<html>`, not arbitrary selector injection. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/css/Properties.kt#L17-L35>

## Pagination Construction In CSS

ReadiumCSS paginates reflowable horizontal text by applying CSS multi-column layout to `:root`, not to a custom inner page node. In `ReadiumCSS-after.css`, `:root` defines `--RS__colWidth`, `--RS__colCount`, `--RS__colGap`, `--RS__maxLineLength`, `--RS__pageGutter`, and `--RS__viewportWidth`, then applies `column-width`, `column-count`, `column-gap`, `column-fill: auto`, `width: var(--RS__viewportWidth)`, `height: 100vh`, and `padding: 0 !important`. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/assets/readium/readium-css/ReadiumCSS-after.css#L7>

The horizontal body rule is `body { width: 100%; max-width: var(--RS__maxLineLength) !important; padding: 0 var(--RS__pageGutter) !important; margin: 0 auto !important; box-sizing: border-box; }`. This confirms that Readium's page gutter is horizontal-only in paginated mode. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/assets/readium/readium-css/ReadiumCSS-after.css#L7>

Scroll mode is not a small variation. The same stylesheet switches `:root[style*=readium-scroll-on]` back to automatic columns and auto height/width. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/assets/readium/readium-css/ReadiumCSS-after.css#L7>

Vertical CJK pagination uses a different generated stylesheet folder and a different axis. `cjk-vertical/ReadiumCSS-after.css` sets `--RS__colWidth: 100vh`, applies vertical writing mode to `:root`, gives `:root` horizontal padding, and gives `body` vertical padding. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/assets/readium/readium-css/cjk-vertical/ReadiumCSS-after.css#L7>

## Pagination Construction In JS And Android

Readium does not use `window.innerWidth` as the final page width on Android. Its JS asks native code for the viewport width, divides by `window.devicePixelRatio`, stores that as `pageWidth`, and writes `--RS__viewportWidth: calc(<native px> / <devicePixelRatio>)`. The source comment says this avoids rounding issues when the device pixel ratio is not an integer. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/assets/_scripts/src/utils.js#L61-L77>

Readium derives page movement from `document.scrollingElement.scrollWidth` and the computed page width. In paginated mode, `scrollRight(...)` and `scrollLeft(...)` move by one `pageWidth`, clamp to the content bounds, and call `scrollToOffset(...)`. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/assets/_scripts/src/utils.js#L194-L230>

Readium snaps offsets to page boundaries using `offset % pageWidth`, and `snapCurrentOffset()` adds half a page before snapping so partial offsets land on the intended page. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/assets/_scripts/src/utils.js#L280-L297>

Readium has a two-column spread repair. If the computed column count per screen is `2` and the content has an odd column count, it appends a zero-width virtual column with `break-before: column` to avoid snapping and page-turn issues. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/assets/_scripts/src/utils.js#L32-L59>

Kotlin navigation still owns the user-facing `goForward` and `goBackward` APIs. For reflowable LTR content, `goForward` calls `webView.scrollRight(animated)` and `goBackward` calls `webView.scrollLeft(animated)`. RTL reverses those calls. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/EpubNavigatorFragment.kt#L869-L900>

At resource boundaries, Readium still uses a `ViewPager` of spine resources. Moving to the next or previous resource calls `resourcePager.setCurrentItem(..., animated)`, then resets the new WebView to the first or last internal page depending on reading progression. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/EpubNavigatorFragment.kt#L903-L944>

`R2WebView` has its own internal page index. `setCurrentItem(item, smoothScroll)` computes `destX = clientWidth * item`; without smooth scrolling it calls `scrollTo(destX, 0)` immediately. Its `numPages` is `computeHorizontalScrollRange() / computeHorizontalScrollExtent()`, rounded and clamped to at least one page. Sources: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/R2WebView.kt#L327-L370> and <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/R2WebView.kt#L1080-L1084>

Readium's progression math uses Android WebView scroll range and extent. In paginated mode it divides `scrollX` by `computeHorizontalScrollRange()`, with a one-page adjustment and reversal for RTL. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/R2BasicWebView.kt#L130-L180>

When opening a locator by progression, Readium converts progression into a target internal WebView page with `(progression * webView.numPages).roundToInt()`, adjusts RTL, and calls `webView.setCurrentItem(item, false)`. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/pager/R2EpubPageFragment.kt#L453-L474>

## Kanshu Extraction Rules

Kanshu should copy the source-backed ideas, not the navigator dependency.

For styling:

- Serve every chapter through a Kanshu-owned resource path before WebView sees it, mirroring Readium's `WebViewServer -> injectHtml -> serveResource` shape.
- Build a deterministic shell with publisher styles first and Kanshu reader styles last.
- Keep style settings as CSS variables on `:root` so runtime updates are one operation.
- Apply variable updates through one JS function that writes `document.documentElement.style.setProperty(name, value, "important")`.
- Include the overflow repair from Readium: force root/body overflow to visible in paginated mode unless a measured probe proves a better scoped fix.
- Do not rely on a parent-only font rule. Kanshu needs explicit descendant rules for body text targets because publisher child selectors can override inherited parent styles.

For pagination:

- Keep CSS multi-column pagination as the first implementation path.
- Make one element the column container. Readium uses `:root`; Kanshu can use `html`/`body` or a dedicated `#kanshu-page`, but the chosen element must have explicit viewport-sized width and height before columns are measured.
- Page width must come from the actual WebView viewport, not guessed CSS. Readium avoids `window.innerWidth` due to Android DPR rounding, so Kanshu should test whether `WebView.width / devicePixelRatio` is more stable than `window.innerWidth` on the Boox target.
- Page count should be derived from scrollable width divided by page width and clamped to at least one page.
- A page turn should set the DOM scroll offset to `pageWidth * pageIndex` immediately. No smooth scroll.
- Recompute page count after font loading, image loading, settings changes, and viewport changes. Navigation should wait until the first measured page count is available.
- If we support two-column spreads later, copy Readium's virtual-column idea or explicitly disable two-column mode. Do not ignore odd-column counts.
- Treat vertical writing as a separate pager axis. Readium forces scroll mode for vertical text in settings resolution and has separate CJK vertical CSS. Kanshu V1 should either fail clearly or build a separate vertical mode, not pretend horizontal pagination supports it.

## Things Not To Copy

- Do not restore `readium-navigator`.
- Do not restore the spine-resource `ViewPager`; it is one source of unwanted animated page/resource transitions on e-ink.
- Do not copy Readium's JS animation path. `scrollToOffset(..., animated = true)` and `animateScrollTo(...)` exist for normal screens, but Kanshu page turns must be instant. Source: <https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/assets/_scripts/src/utils.js#L221-L278>
- Do not copy ReadiumCSS wholesale without understanding the cascade. Kanshu wants the Kindle-style split from `docs/KINDLE_TYPOGRAPHY.md`: publisher structure is useful, but legibility belongs to the reader.
- Do not use `RsProperties` or `UserProperties` as an arbitrary CSS injection mechanism. They emit inline properties on `<html>`, not selector rules or `<style>` blocks.

## Open Probes Before Code

These probes should be answered with tiny instrumented experiments before the next implementation PR grows:

1. Does `window.innerWidth`, `document.documentElement.clientWidth`, or `WebView.width / devicePixelRatio` produce the most stable page width on Boox Go 7 Gen 2 B&W?
2. Should the column container be `html`, `body`, or `#kanshu-page`? The previous reset happened because the custom page container collapsed to a 64px height. The next spike must log computed `height`, `clientHeight`, `scrollWidth`, `scrollHeight`, `columnWidth`, and `columnGap` on first layout.
3. Does `height: 100vh` on the column container work reliably inside Android WebView when the host Compose view has inset/immersive changes?
4. Does forcing root/body `overflow: visible !important` fix books with `overflow-x: hidden` without reintroducing unwanted vertical scroll?
5. Do late-loading images or fonts change `scrollWidth` after the first `load` event on real EPUBs? If yes, the settling protocol must wait for `document.fonts.ready`, image decode/load events, and stable `scrollWidth` across animation frames.
6. Is `round`, `ceil`, or `floor` the right page-count operation for Kanshu? Readium uses rounded `computeHorizontalScrollRange() / computeHorizontalScrollExtent()` on Android. Kanshu's JS-only measurement may need `Math.ceil(scrollWidth / pageWidth)` to avoid dropping a partial final page.

## Recommended First Implementation Slice After This Doc

The next code PR should be a measurement spike, not the full reader:

- Load one sanitized chapter into a Kanshu shell.
- Inject only minimal pagination CSS and a diagnostic JS bridge.
- Render no reader chrome except the existing route shell.
- Log computed style and geometry values needed by the probes above.
- Add one local debug-only sample path if a real Kavita book is too slow for iteration.

Only after those measurements are understood should we rebuild navigation, progress persistence, settings, and overlay UI.
