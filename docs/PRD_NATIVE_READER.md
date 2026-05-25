# PRD: Native Reader Engine

## What Is This?

Kanshu replaces its WebView-based EPUB renderer with a native Android text engine hosted by Compose. The WebView is removed from the reader rendering path entirely. Text measurement and rendering both use Android `StaticLayout` + `TextPaint`; Compose hosts the page surface with `Canvas` and renders reader chrome, overlays, and popups.

This is not a refactor of the existing reader. It is a new engine.

## Why

The WebView creates a permanent boundary between the browser's layout/selection/interaction world and Compose. Every native feature — selection popups, highlights, tap zones, e-ink refresh control, typography preferences — requires bridging that boundary. On the Boox Go 7's weak SoC, that bridge has real cost: WebView process overhead, Chromium's multi-process architecture, JS round-trips for every page turn, and no direct control over when and how the screen redraws.

Kindle does not use a WebView. It renders text natively and owns the full interaction surface. Kanshu should do the same.

## North Star

The reader should feel like a native e-ink text surface, not an app wrapped around a browser. Page turns should be instant. Selection should produce a native popup. Typography preferences should apply without a repagination round-trip through JavaScript. The engine should be fast enough on the Boox Go 7 that performance is never noticeable.

## Constraints

- Target device: Boox Go 7 Gen 2 B&W (mid-range MediaTek SoC, Android 12 / API 31).
- No animations, ripples, fades, or transitions (unchanged from main PRD).
- Touch targets >= 48dp (unchanged from main PRD).
- Corpus: general reflowable EPUBs from the user's Kavita library — fiction, non-fiction, mixed. No manga, no fixed-layout EPUB.
- Script support: Latin-script reflowable text in Phase 0–3. CJK, RTL, and bidi are out of scope until explicitly scheduled; opening such a book may render poorly — that is acceptable for now, not a Phase 0 failure.

### Corpus and Fidelity Expectations

Kanshu does not evaluate publisher CSS live. Structure is inferred from XHTML tags, not computed styles. That is intentional and matches the Kindle-style split in `docs/KINDLE_TYPOGRAPHY.md`, but it means fidelity is phased:

| Phase | Reading experience                                                                                                                                                             |
| ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 0–2   | Readable text with lossy structure. Emphasis, headings, quotes, lists, images land incrementally. Tables, floats, multi-column layouts, and footnote popups are not goals yet. |
| 3     | Interactive text: links, footnotes, multi-word selection, highlights.                                                                                                          |
| 4+    | Progress, TOC, chapter navigation, Kavita sync.                                                                                                                                |

A broken table layout is not a Phase 0 failure. Unreadable text or unacceptable Boox performance is.

## Architecture

### Pipeline

```
Kavita EPUB
  |
  v
Readium Streamer (readium-shared + readium-streamer)
  |
  v
Publication.readingOrder -> resource bytes (XHTML)
  |
  v
Jsoup XHTML parser
  |
  v
ReaderDocument (List<ReaderBlock>)
  |
  v
ReaderLayoutEngine (StaticLayout + TextPaint)
  |
  v
List<ReaderPage> (carrying precomputed StaticLayout artifacts)
  |
  v
Compose Canvas renderer draws StaticLayout artifacts
```

### What We Keep From Readium

- `readium-shared` — `Publication`, `Manifest`, `Link`, `Container`, `Url` types.
- `readium-streamer` — `PublicationOpener` for EPUB container unpacking, OPF parsing, spine ordering, resource resolution.

We use `Publication` to enumerate `readingOrder` and read resource bytes. The only change from today: instead of passing HTML to a WebView, we parse it into `ReaderDocument` and render natively.

Readium is retained for EPUB I/O only. `readium-navigator` is gone because the WebView renderer is the boundary we are eliminating — not because Readium failed at unpacking EPUBs.

### What We Remove

- `readium-navigator` (already removed).
- All WebView code in `ReaderScreen.kt`.
- `ChapterHtmlExtractor` (replaced by the structured parser).
- JavaScript pagination, `DiagnosticBridge`, `paginatedHtml()`.
- WebView touch handling (replaced by Compose tap zones).

## Reader Document Model

XHTML is converted into an inspectable, renderable document model before layout.

```kotlin
data class ReaderDocument(
  val blocks: List<ReaderBlock>,
  val language: String? = null, // from `<html lang>` or `<body xml:lang>` — default BreakIterator locale
)

data class ParseDiagnostics(
  val unsupportedBlockTags: Map<String, Int>,  // e.g. "table" -> 3
  val unsupportedInlineTags: Map<String, Int>, // e.g. "ruby" -> 1
)

data class ParseResult(
  val document: ReaderDocument,
  val diagnostics: ParseDiagnostics,
)

sealed interface ReaderBlock

data class ParagraphBlock(
  val spans: List<TextSpan>,
) : ReaderBlock

data class HeadingBlock(
  val level: Int, // 1-6
  val spans: List<TextSpan>,
) : ReaderBlock

data class QuoteBlock(
  val children: List<ReaderBlock>,
) : ReaderBlock

data class ListBlock(
  val ordered: Boolean,
  val items: List<ListItem>,
) : ReaderBlock

data class ListItem(
  val blocks: List<ReaderBlock>,
)

data object HorizontalRule : ReaderBlock

data class ImageBlock(
  val resourceHref: String,
  val alt: String?,
) : ReaderBlock
```

`ReaderDocument.language` is document-level only in Phase 0–3. Real EPUBs often set `xml:lang` on individual `<p>` or `<span>` elements for foreign phrases; that is an extension point for a later phase (`language` on `ReaderBlock` or `TextSpan` when CJK or mixed-language support is scheduled). Phase 0 uses document-level locale as the `BreakIterator` default.

The parser returns `ParseResult`, not bare `ReaderDocument`. `ParseDiagnostics` is the side-channel for unsupported tag counts — the block model stays clean; the feature module forwards diagnostics to the panel.

Inline text:

```kotlin
sealed interface TextSpan

data class TextLeaf(
  val text: String,
  val style: InlineStyle = InlineStyle.Plain,
) : TextSpan

data class StyledGroup(
  val style: InlineStyle,
  val children: List<TextSpan>,
) : TextSpan

data class LinkSpan(
  val href: String,
  val children: List<TextSpan>,
) : TextSpan

enum class InlineStyle {
  Plain, Bold, Italic, BoldItalic, SmallCaps,
}
```

The parser builds a tree that preserves nesting (e.g., bold inside italic resolves to `BoldItalic` at the `StyledGroup` level). The layout engine flattens the tree to a `SpannableString` (with Android text spans for bold/italic/etc.) when building each block's `StaticLayout`. This avoids premature flattening during parsing while keeping the layout step simple.

### Reference: Ares `:htmlparser` (ideas, not a dependency)

The sibling project **Ares** (`../ares`, module `:htmlparser`) solves HTML → native UI for scroll-based RSS articles. Kanshu does **not** depend on it — Ares renders via Compose `LazyColumn` + `BasicText`; Kanshu paginates with `StaticLayout` + Canvas. Different render engines, different constraints.

Use Ares as a **reference** when implementing `EpubParser`:

| Ares artifact          | What to borrow                                                           | Kanshu delta                                                                                                   |
| ---------------------- | ------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------- |
| `InlineStyleExtractor` | Recursive DOM walk: inherit styles/links on descent, emit at text leaves | Emit `TextSpan` **tree** (`StyledGroup`, `LinkSpan`), not flat spans; flatten at layout time                   |
| `FallbackTagHandler`   | Text is never lost — always extract and promote children                 | Same invariant; silent unwrap + `ParseDiagnostics` counts instead of visible `UnsupportedTag` boxes            |
| `TagHandlerRegistry`   | Per-tag handlers registered against Jsoup tag names                      | Same shape for block vs inline handlers; EPUB-specific rules (structural `<div>` unwrap, spine-relative hrefs) |
| `RealContentTest`      | Fixture tests against real fetched HTML                                  | Same approach with Kavita EPUB chapter XHTML                                                                   |

Do not import `:htmlparser` or share a module yet. The ASTs diverge (`ParsedContent` vs `ReaderBlock`) and the render paths are incompatible. Revisit a shared parser-only library only if both projects stabilize on identical block semantics.

This model preserves structural intent (headings, quotes, list nesting, emphasis) without carrying live CSS. It follows the Kindle-style split from `docs/KINDLE_TYPOGRAPHY.md`: publisher CSS shapes structure, Kanshu owns legibility and spacing.

### XHTML Element Mapping

| XHTML element             | ReaderBlock                                                                                     |
| ------------------------- | ----------------------------------------------------------------------------------------------- |
| `<p>`                     | `ParagraphBlock`                                                                                |
| `<div>`                   | Inspect children first (Jsoup DOM): unwrap if any block-level child; otherwise `ParagraphBlock` |
| `<section>`, `<article>`  | Unwrap block children (structural wrappers, not paragraphs)                                     |
| `<h1>`–`<h6>`             | `HeadingBlock`                                                                                  |
| `<blockquote>`            | `QuoteBlock`                                                                                    |
| `<ul>`, `<ol>`            | `ListBlock`                                                                                     |
| `<hr>`                    | `HorizontalRule`                                                                                |
| `<img>`                   | `ImageBlock` (href resolved against spine item base path at parse time)                         |
| `<span>`                  | Unwrap inline children                                                                          |
| `<em>`, `<i>`             | `TextSpan(style = Italic)`                                                                      |
| `<strong>`, `<b>`         | `TextSpan(style = Bold)`                                                                        |
| `<a href="...">`          | `LinkSpan(href, children)` — carries href for Phase 3 tap handling                              |
| `<br>`                    | `TextSpan(text = "\n")`                                                                         |
| `<sub>`, `<sup>`          | Unwrap; text preserved as plain (styling deferred)                                              |
| `<table>` and descendants | Unwrap text into surrounding flow (Phase 1+ may add table support)                              |
| Unknown elements          | Unwrap children into parent block                                                               |

### Degradation Policy

Unknown or unsupported elements never crash the parser. Text is always promoted — never dropped — by unwrapping children into the parent block.

**Reading surface:** silent. No placeholder boxes, no `[unsupported: <table>]` chrome in production. The reader stays clean; structural loss is acceptable in early phases.

**Diagnostics:** `EpubParser.parse()` returns `ParseDiagnostics` alongside `ReaderDocument` via `ParseResult`. While walking the DOM, the parser increments `unsupportedBlockTags` and `unsupportedInlineTags` for elements handled by unwrap-only rules (unknown tags, `<table>`, deferred inline tags like `<ruby>`, etc.). The feature module passes `ParseResult.diagnostics` to the diagnostics panel (e.g., `table: 3`, `aside: 1`). Parser unit tests assert text preservation for representative markup from the Kavita library. Tag counts guide which mappings to add next.

**Images:** if bounds resolution fails (corrupt file, missing resource), reserve a fixed-height placeholder (`alt` text or `[image]` label) so pagination stays stable and the gap is visible without reflow when a late decode succeeds.

## Layout Engine

The layout engine builds `StaticLayout` objects for each block and uses their heights to determine page boundaries. The `TextPaint` configuration is injected via a factory so the engine is testable — tests provide a controlled factory, production provides one configured from `ReaderPreferences`.

```kotlin
data class ReaderViewport(
  val widthPx: Int,
  val heightPx: Int,
  val density: Float,
)

data class BlockStyle(
  val paint: TextPaint,
  val lineSpacingMultiplier: Float,
  val lineSpacingAdd: Float,
  val hyphenationFrequency: Int,
  val alignment: Layout.Alignment,
  val breakStrategy: Int,
  val indentPx: Float,         // horizontal indent (cumulative for nested blocks)
  val prefixWidthPx: Float,    // bullet/number gutter or quote border inset — reduces measurement width
  val marginTopPx: Float,
  val marginBottomPx: Float,
)

data class ReaderPage(
  val entries: List<PageEntry>,
)

sealed interface PageEntry {
  val blockIndex: Int
  val yOffsetPx: Float      // vertical position within the page
  val visibleHeightPx: Float // actual visible height of this entry

  data class FullBlock(
    override val blockIndex: Int,
    override val yOffsetPx: Float,
    override val visibleHeightPx: Float,
    val layout: StaticLayout,
  ) : PageEntry

  data class SplitBlock(
    override val blockIndex: Int,
    override val yOffsetPx: Float,
    override val visibleHeightPx: Float,
    val layout: StaticLayout,
    val lineRange: IntRange,    // which lines of the layout to draw
    val firstLineTopPx: Float,  // layout.getLineTop(lineRange.first) — avoids recalculation
  ) : PageEntry
}

class ReaderLayoutEngine {
  fun layout(
    document: ReaderDocument,
    viewport: ReaderViewport,
    styleResolver: (ReaderBlock) -> BlockStyle,
    contentWidthPx: Int,
    imageBoundsResolver: (resourceHref: String) -> ImageBounds?,
  ): List<ReaderPage>
}

data class ImageBounds(
  val intrinsicWidthPx: Int,
  val intrinsicHeightPx: Int,
)
```

### Image Dimension Resolution

The pagination engine must know the exact height of every block — including images — to determine page boundaries. Image pixel data is decoded lazily during rendering, but image **dimensions** are resolved during pagination.

`imageBoundsResolver` reads image headers using `BitmapFactory.Options.inJustDecodeBounds = true`. This decodes only the file header (a few bytes), returns intrinsic width/height in microseconds, and allocates zero pixel buffers on the JVM heap.

The engine scales the intrinsic dimensions to fit `contentWidthPx` (preserving aspect ratio) and uses the scaled height as the image block's physical height during page accumulation. If bounds resolution fails (corrupt image, missing resource), reserve a fixed placeholder height (one line of body text by default) and render `alt` text or a `[image]` label inside it. Pagination uses the placeholder height; no reflow when a late decode succeeds.

The renderer decodes the actual bitmap lazily (scaled to the known dimensions), drawing the placeholder label until the bitmap is ready. Since the height is fixed at pagination time, no reflow is needed when the bitmap arrives.

The layout engine builds one `StaticLayout` per block during pagination using the full `BlockStyle` — not just `TextPaint`, but also `StaticLayout.Builder`-level properties (line spacing, hyphenation). That same `StaticLayout` is both the measurement artifact (its height determines page splits) and the rendering artifact (the renderer draws it directly). There is no separate measurement-vs-rendering divergence — one object controls both.

For split blocks (long paragraphs spanning multiple pages), the engine stores which lines belong on each page. A split block reuses the same `StaticLayout` instance across all `SplitBlock` entries for that source block — the engine never rebuilds a `StaticLayout` per split page. `visibleHeightPx` is the height of the visible line range (not `StaticLayout.height`), and `firstLineTopPx` caches `layout.getLineTop(lineRange.first)` so the renderer and hit-testing don't recalculate it. The renderer clips drawing to those lines.

`styleResolver` maps each block type to its complete style configuration. Production resolves from `ReaderPreferences` + block type (e.g., headings get larger `TextPaint` size and more `marginTopPx`). Tests inject a controlled resolver.

### TextPaint Thread Safety

`TextPaint` is mutable and not thread-safe. The pagination thread and UI thread (Canvas redraw) must never share mutable references to the same `TextPaint` instance.

**Invariant:** The `styleResolver` returns freshly constructed `TextPaint` instances per layout job. The pagination thread owns those instances exclusively during `StaticLayout` construction. `TextPaint` instances are treated as immutable after layout construction — no code path may mutate a `TextPaint` that has been used to build a `StaticLayout`. `StaticLayout.draw()` reads from the paint but does not mutate it. Once pagination completes and the `List<ReaderPage>` is handed to the UI thread for rendering, the pagination thread no longer touches those paints.

If a preference change arrives mid-pagination, the caller cancels the in-flight job and starts a new one with a new `styleResolver` producing new `TextPaint` instances. The old job's paints and layouts are discarded entirely — no shared state between layout generations.

**Implementation note:** `BlockStyle` carries `TextPaint` in this spec for clarity, but the implementation should consider making paint construction a factory concern (e.g., `BlockStyle` holds immutable value descriptors like `textSizePx`, `typeface`, `fakeBold`; a factory creates `TextPaint` from them). This makes mutation structurally impossible rather than relying on discipline.

### StaticLayout Construction

Each `StaticLayout` is built with the full `BlockStyle`. The measurement width is reduced by horizontal insets so line breaks match what is drawn:

```kotlin
val localWidthPx = (contentWidthPx - style.indentPx - style.prefixWidthPx)
  .toInt()
  .coerceAtLeast(1)
StaticLayout.Builder.obtain(text, 0, text.length, style.paint, localWidthPx)
  .setAlignment(style.alignment)
  .setLineSpacing(style.lineSpacingAdd, style.lineSpacingMultiplier)
  .setHyphenationFrequency(style.hyphenationFrequency)
  .setBreakStrategy(style.breakStrategy)
  .build()
```

The renderer translates the Canvas horizontally by `style.indentPx + style.prefixWidthPx` before calling `StaticLayout.draw()`. Because the layout was measured at the reduced width, rendered text fits within the content column and aligns with the right margin.

### Horizontal Inset Model

Two inset fields compose the horizontal layout budget. Both reduce measurement width; both shift the draw origin.

| Field           | Used for                                  | Example                                     |
| --------------- | ----------------------------------------- | ------------------------------------------- |
| `indentPx`      | Nested structure, block-quote text offset | Quote inside quote: cumulative indent steps |
| `prefixWidthPx` | Prefix drawn before text                  | Bullet/number column, quote border gutter   |

**Lists:** measure item text at `contentWidthPx - prefixWidthPx`. Draw the bullet or number in `[0, prefixWidthPx)`, then `StaticLayout.draw()` at `x = prefixWidthPx`. Never measure at full width and draw with a horizontal offset — line breaks will not match the visual column.

**Block quotes:** `prefixWidthPx` reserves the left border gutter; `indentPx` offsets the quote body. The border is drawn in the gutter; text is measured and drawn after both insets.

For nested blocks (e.g., a quote inside a quote), `indentPx` is cumulative — the `styleResolver` sums indent steps based on nesting depth.

Properties that live on `TextPaint`: font family, font size, font weight, text color, letter spacing, word spacing.

Properties that live on `StaticLayout.Builder`: alignment, line spacing (multiplier + add), hyphenation frequency, break strategy, width (derived from `contentWidthPx - indentPx - prefixWidthPx`).

Both are encapsulated in `BlockStyle` so the engine has everything it needs to construct a `StaticLayout` without reaching back to `ReaderPreferences`.

The layout engine is a pure function: input → output. It does not own cancellation. The caller (ViewModel coroutine) checks `isActive` between blocks or injects a `shouldContinue: () -> Boolean` callback. This keeps the engine testable without ViewModel lifecycle coupling.

### Block Spacing Model

Each block type has vertical margins derived from preferences:

| Block type      | marginTopPx                          | marginBottomPx                 |
| --------------- | ------------------------------------ | ------------------------------ |
| Paragraph       | 0                                    | `preferences.paragraphSpacing` |
| Heading (h1–h3) | `fontSize * 1.2`                     | `fontSize * 0.6`               |
| Heading (h4–h6) | `fontSize * 0.8`                     | `fontSize * 0.4`               |
| Block quote     | `preferences.paragraphSpacing`       | `preferences.paragraphSpacing` |
| List block      | `preferences.paragraphSpacing * 0.5` | `preferences.paragraphSpacing` |
| Horizontal rule | `preferences.paragraphSpacing`       | `preferences.paragraphSpacing` |
| Image           | `preferences.paragraphSpacing`       | `preferences.paragraphSpacing` |

Adjacent margins collapse: `gap = max(prevBlock.marginBottom, currBlock.marginTop)`. The first block on a page has no top margin (it starts at the page's content edge). A split block continuing from the previous page has no top margin either.

### Pagination Algorithm

1. Receive `List<ReaderBlock>` for the chapter, viewport dimensions, and a `styleResolver`.
2. Walk blocks top-to-bottom. For each block:
   a. Resolve its `BlockStyle` via the `styleResolver`.
   b. Flatten the span tree to `SpannableString` (with Android text spans for bold/italic/etc.).
   c. Build a `StaticLayout` at `contentWidthPx - indentPx - prefixWidthPx` with the full `BlockStyle`.
3. Compute inter-block spacing with **margin collapsing**: the vertical gap between two adjacent blocks is `max(previousBlock.style.marginBottomPx, currentBlock.style.marginTopPx)`, not the sum. This matches CSS collapsing behavior and prevents double-spacing at heading/paragraph boundaries.
4. Accumulate block heights + collapsed margins into the current page until the running height exceeds page height.
5. When a block overflows:
   - If it is the first block on the page (very long paragraph), split at a line boundary. Use `StaticLayout.getLineBottom(lineIndex)` to find the last line that fits within remaining page height. Produce a `SplitBlock` with the line range for this page; the remaining lines start the next page with zero top margin (the paragraph is mid-flow). If the remaining lines still exceed a full page, split again — repeat until the block is fully consumed.
   - Otherwise, the block moves to the next page as the first entry. If it still overflows as first block on that fresh page (block taller than page height), split it as above.
6. Heading blocks include their top margin in the space calculation. A heading that fits but leaves no room for at least one line of the next block moves to the next page (orphan prevention).
7. Output: `List<ReaderPage>`. Each page entry carries its `StaticLayout`, y-offset (incorporating collapsed margins), and block index. Pages are indexed. Page turns swap which page the Canvas draws.

The caller is responsible for cancellation. Between blocks, the caller checks whether the layout job is still relevant (e.g., coroutine `isActive` or an injected `shouldContinue` callback). If cancelled, the partial result is discarded and a new layout is started with current parameters.

### Layout Cache

Pagination results are cached by a composite key: `bookId + spineHref + viewport + preferences hash`. On chapter reentry with unchanged preferences and viewport, the cached pages are reused without remeasurement. The cache holds at most one chapter's pages (the current chapter). Preference changes invalidate the cache and trigger repagination.

### Text Engine

**Android `StaticLayout` + `TextPaint` for both measurement and rendering.** The same `StaticLayout` object that determines page splits is drawn directly onto the Compose `Canvas`. There is no separate measurement engine and rendering engine — one object controls line breaks, heights, character positions, and pixel output. This eliminates measurement/rendering parity risk entirely.

`StaticLayout` talks directly to native C++ text measurement, permits reuse of `TextPaint` instances across blocks, and has lower expected allocation overhead than WebView or Compose text measurement. It is battle-tested for large text blocks (it is what `TextView` uses internally) and is widely used off-main-thread in production apps. Off-main-thread safety is expected to hold for Kanshu's inputs (single font family, simple spans, no exotic BiDi) but is validated in the Phase 0 spike rather than assumed.

**Off-main fallback policy:** If `StaticLayout` construction produces crashes or corrupt layouts when called from `Dispatchers.Default`, the fallback is a single-threaded layout dispatcher (`newSingleThreadContext("layout")`) that serializes all `StaticLayout` construction. This is slower but avoids contention. If even that fails, construction moves to main-thread chunking (build N layouts per frame, yield between chunks). Neither fallback is expected to be needed — this is documented so the spike has a known path forward if validation fails.

## Performance

Performance is not a secondary concern. The Boox Go 7 has a mid-range SoC. Every millisecond of layout, measurement, and rendering is felt by the user because e-ink refresh latency (~300ms) makes any additional delay obvious. E-ink refresh latency must not be allowed to hide slow code — measure computation and rendering independently of hardware refresh.

### Performance Budget

All measurements on the Boox Go 7 Gen 2 B&W with representative EPUBs from the user's Kavita library. Budgets are split into three layers to prevent e-ink refresh from masking slow computation.

| Operation                   | Layer                 | Budget              | Notes                                                                                                                                                                                                                                                            |
| --------------------------- | --------------------- | ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Page turn                   | Layout computation    | 0ms                 | No measurement. Page list with precomputed StaticLayouts is ready.                                                                                                                                                                                               |
| Page turn                   | Canvas redraw         | < 16ms              | Swap page index, draw precomputed StaticLayouts at stored y-offsets.                                                                                                                                                                                             |
| Page turn                   | E-ink visible refresh | ~300ms              | Hardware-dependent. Measured separately, not optimized in software beyond EPD mode selection.                                                                                                                                                                    |
| Chapter pagination (eager)  | Layout computation    | < 200ms (preferred) | Measure all blocks on `Dispatchers.Default` using `StaticLayout`. Not a hard gate — lazy pagination is acceptable if first page is fast.                                                                                                                         |
| First page ready            | End-to-end            | < 300ms (required)  | Parse + paginate enough for page 1. Users feel first-page latency.                                                                                                                                                                                               |
| Preference change           | Layout computation    | < 200ms             | Cancel stale layout job, repaginate with new preferences.                                                                                                                                                                                                        |
| Preference change           | Visible update        | < 500ms             | Computation + Compose recomposition + e-ink refresh.                                                                                                                                                                                                             |
| First chapter render (full) | End-to-end            | < 800ms             | `OpenBookUseCase` → XHTML read → parse → full chapter paginate → first page drawn. Includes network/disk I/O.                                                                                                                                                    |
| Memory (chapter in-flight)  | Steady-state          | < 5MB (target)      | Text-only chapters: block model + page entries + `StaticLayout` artifacts. Excludes font caches, image decode buffers, and decoded bitmaps. Validate on representative chapters during the Phase 0 spike; if exceeded, lazy pagination reduces retained layouts. |

### Hard Rules

- **Page turns never trigger measurement or layout construction.** The page list with precomputed `StaticLayout` artifacts is ready before any page turn. A page turn is an index swap and a Canvas redraw of pre-built layouts. Any code path that builds a `StaticLayout` during a page turn is a bug.
- **One Canvas per page.** The renderer draws only the current page's entries. No off-screen rendering, no page prefetch. If lazy pagination becomes necessary, one-page-ahead layout precomputation is allowed but rendering is always single-page.

### Performance-Critical Design Decisions

**Blocks, not one giant string.** The chapter is a `List<ReaderBlock>`, not a single `AnnotatedString`. Each block gets its own `StaticLayout`. This bounds per-layout cost (a single paragraph vs. a 50K-character chapter) and avoids allocation cliffs on large inputs.

**Build once, draw many.** `StaticLayout` objects are built once during pagination on `Dispatchers.Default`. The output is `List<ReaderPage>` carrying precomputed layouts. Page turns draw those layouts at stored y-offsets — no layout construction, no text measurement, just `StaticLayout.draw(canvas)`.

**Single text engine.** `StaticLayout` is both the measurement artifact and the rendering artifact. There is no second text engine to diverge from. Line breaks, heights, and character positions are determined once and drawn exactly as computed.

**Geometry is available from layout.** `StaticLayout` exposes `getLineCount()`, `getLineStart()`, `getLineEnd()`, `getLineTop()`, `getLineBottom()`, `getOffsetForHorizontal()`, and `getLineForVertical()`. Hit-testing, selection, and highlights use these APIs directly on the precomputed layouts. No separate geometry-retention pass during rendering.

### Profiling Protocol

Every performance claim must be validated on-device before shipping. The spike includes a diagnostics panel (carried over from the current reader) that reports:

- Chapter block count.
- Unsupported tag counts from the parser (block and inline).
- Pagination wall-clock time (ms), split into: total, p50/p95/max per-block `StaticLayout` construction.
- Page count.
- Canvas draw time for current page (ms).
- E-ink refresh time is measured via `adb shell dumpsys gfxinfo` frame stats, not in-app.

### Fallback Ladder

If the spike fails the budget on representative books:

1. Profile per-block measurement to find outliers (long paragraphs, complex spans).
2. If outliers dominate, split long blocks before measurement (e.g., paragraphs > 2000 characters get pre-split at sentence boundaries).
3. If bulk measurement is the bottleneck, paginate lazily (measure only the next N pages). Progress uses character-offset percentage instead of page count. The page list grows as the user reads forward. The architecture supports this without changes to the renderer.

## Rendering

Each page is rendered by a single Compose `Canvas`. The page renderer iterates over precomputed `PageEntry` items and calls `StaticLayout.draw(nativeCanvas)` at the stored y-offsets. Because the same `StaticLayout` that determined page splits is drawn directly, line breaks, heights, and positions are guaranteed to match.

For `SplitBlock` entries, the Canvas clips to the line range before drawing — `canvas.clipRect()` to the vertical bounds of the visible lines, then `canvas.translate()` to offset the `StaticLayout` so the correct lines are visible.

Block-type-specific rendering:

- **Paragraphs and headings** — drawn directly via `StaticLayout.draw()`. Heading `TextPaint` uses scaled font size per level.
- **Block quotes** — `canvas.drawLine()` in the `prefixWidthPx` gutter, then `StaticLayout.draw()` after `indentPx + prefixWidthPx`.
- **Lists** — bullet or number prefix drawn in `[0, prefixWidthPx)`; item text measured and drawn at `prefixWidthPx` offset.
- **Images** — loaded from EPUB resources via `Publication.get(resourceHref).read()` → `BitmapFactory` → `Bitmap`. Intrinsic dimensions scaled to fit column width (`viewport.widthPx - horizontalMargins`), preserving aspect ratio. Drawn via `canvas.drawBitmap()`. Cached in ViewModel. Failed loads draw the fixed placeholder from pagination.
- **Horizontal rules** — `canvas.drawLine()` across the column width.

Compose still owns everything around the page surface: tap zones, reader chrome overlay, settings UI, popups, and navigation controls. Only book text rendering goes through Canvas.

### Typography Model

From `docs/KINDLE_TYPOGRAPHY.md` §5:

- **Kanshu owns:** font family, font size, line height, margins, alignment, paragraph spacing, word spacing, letter spacing.
- **Publisher owns:** structural semantics (headings, quotes, list nesting, emphasis).

User preferences (`ReaderPreferences` in `core:model`) drive the rendering step. The document model preserves structure; rendering applies user typography.

## Selection

Selection is not based on Compose's `SelectionContainer`. Since the page surface is a `Canvas` drawing `StaticLayout` artifacts, selection uses `StaticLayout`'s native geometry APIs directly.

### Phase 0: Feasibility Slice

Phase 0 includes a minimal selection proof: long-press selects one word, draws a highlight rect on Canvas, and shows a placeholder Compose popup anchored to the word. No drag handles, no multi-word, no persistence. This validates the touch → geometry → popup pipeline early.

### Durable Model: Custom Hit-Testing Over StaticLayout Geometry

`StaticLayout` exposes everything needed for coordinate-to-character hit-testing:

- `getLineForVertical(y)` — which line is at a given y coordinate.
- `getOffsetForHorizontal(line, x)` — which character offset is at a given x on that line.
- `getLineStart(line)` / `getLineEnd(line)` — character range for a line.
- `getLineTop(line)` / `getLineBottom(line)` — vertical bounds for a line.
- `getPrimaryHorizontal(offset)` — x position of a character.

Combined with each `PageEntry`'s stored `blockIndex` and `yOffsetPx`, this gives precise coordinate-to-text-position mapping without any additional geometry-retention pass.

```kotlin
data class TextPosition(
  val blockIndex: Int,
  val offset: Int, // character offset into the block's flattened text
)

data class ReaderSelection(
  val start: TextPosition,
  val end: TextPosition,
)
```

Selection flow:

1. Touch coordinates → find the `PageEntry` whose y-range contains the touch (using `yOffsetPx` + `visibleHeightPx`). For `SplitBlock`, account for `firstLineTopPx` offset.
2. Translate touch y to local layout coordinates (`touchY - entry.yOffsetPx + entry.firstLineTopPx` for splits, or `touchY - entry.yOffsetPx` for full blocks).
3. `StaticLayout.getLineForVertical(localY)` → line index.
4. `StaticLayout.getOffsetForHorizontal(line, touchX)` → character offset.
5. Expand to word boundaries using `BreakIterator` on the flattened text (on-demand; Phase 0 records timing). Use the chapter's `xml:lang` when present; fall back to the system default locale.
6. Compute one or more highlight rects from `StaticLayout.getLineTop/Bottom` and `getPrimaryHorizontal`. A word may span lines (hyphenation or wrap), producing multiple rects.
7. Draw highlight rects on the Canvas. Show a Compose popup anchored to the bounding rect of the selection.

This model supports persistent highlights, multi-color annotations, and cross-page selections from day one because it operates on stable `TextPosition` ranges stored independently of transient UI state.

### Link and Footnote Taps (Phase 3)

Links use the same hit-testing pipeline as selection. The key insight: `ClickableSpan` and `URLSpan` are inert when drawn via `StaticLayout.draw(canvas)` — Canvas has no built-in touch routing. Instead:

1. **Non-clickable metadata spans.** When flattening the span tree to `SpannableString`, link elements map to a custom annotation span (e.g., `EpubLinkSpan(resourceHref: String)`) rather than `ClickableSpan`. These spans carry metadata but have no click behavior.

2. **Tap resolution via geometry.** On a regular tap (not long-press), the same pipeline resolves coordinates to a character offset. Then query the `Spannable` for metadata spans at that offset:

   ```kotlin
   val spannable = entry.layout.text as Spannable
   val links = spannable.getSpans(offset, offset, EpubLinkSpan::class.java)
   if (links.isNotEmpty()) onLinkTapped(links.first().resourceHref)
   ```

3. **Routing.** `onLinkTapped` resolves the href — same-chapter fragment → scroll to anchor, cross-chapter → load spine item, footnote → show popup. All in Kotlin, no View overlay needed.

This proves the Canvas approach handles interactive text (links, footnotes) without `TextView` touch interception or View-based workarounds.

## Module Structure

| Module                                     | Responsibility                                                                                                                                                                                           |
| ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `:reader-navigator` (new, Android library) | The native text engine. Parser (XHTML → `ReaderDocument`), block model types, layout engine (`StaticLayout`), Canvas page renderer, selection/hit-testing, and the public `ReaderPageViewer` composable. |
| `core:model` (existing)                    | `ReaderPreferences`, `ReaderFont`, `ReaderAlignment`, `ReaderMargins`.                                                                                                                                   |
| `core:data` (existing)                     | `OpenBookUseCase`, `ReaderSource`, `ReaderResult`, `Publication` ownership.                                                                                                                              |
| `features:reader:app` (existing)           | Thin feature shell: `ReaderScreen`, `ReaderViewModel`, chrome, tap zones, diagnostics, progress, chapter sequencing.                                                                                     |

### `:reader-navigator` Internal Structure

```
reader-navigator/
  src/main/kotlin/com/charliesbot/kanshu/navigator/
    model/          — ReaderDocument, ReaderBlock, TextSpan, InlineStyle, LinkSpan
    parser/         — EpubParser (Jsoup XHTML → ParseResult)
    engine/         — ReaderLayoutEngine, BlockStyle, PageEntry, StaticLayout construction
    render/         — PageCanvas, BlockRenderer
    selection/      — HitTesting, WordSelector, highlight drawing
    ReaderPageViewer.kt  — public Composable entry point
  src/test/         — parser JVM unit tests (Jsoup, no Android)
  src/androidTest/  — layout engine instrumented tests (real StaticLayout)
```

### Public API

```kotlin
@Composable
fun ReaderPageViewer(
  document: ReaderDocument,
  preferences: ReaderPreferences,
  currentPage: Int,
  onPageCount: (Int) -> Unit,
  onWordSelected: (TextPosition, String, Rect) -> Unit,
  onLinkTapped: (String) -> Unit,
  modifier: Modifier = Modifier,
)
```

The viewer handles layout, pagination, Canvas rendering, and selection internally. The consumer provides content, preferences, and reacts to callbacks.

**Lazy pagination and page count:** `onPageCount` reports the number of pages measured so far. Under eager pagination, this equals the full chapter page count once layout completes. Under lazy pagination, the count grows as the reader advances; UI may show "page X" without a total until layout catches up. Progress persistence does not use page count — see Progress Model below.

**Phase 0 defaults:** `ReaderPreferences` may be hard-coded in the viewer until Phase 2 wires user settings. Phase 0 still accepts a `preferences` parameter so the public API and typography path are stable from the first slice.

**Coordinate space:** `Rect` in `onWordSelected` is in Compose layout coordinates (relative to the `ReaderPageViewer` composable's bounds), not page-local or window coordinates. This allows the consumer to anchor a Compose popup directly without coordinate conversion. Phase 0 verifies this on-device.

**Resource resolution (Phase 1+):** When image and link support lands, the API adds a `resourceResolver: ReaderResourceResolver` parameter for loading image bitmaps and resolving link hrefs. Phase 0 omits this — the viewer renders text-only content without needing external resources.

### Why a Top-Level Module

The native text engine (parser, layout, renderer, selection) is a different scale than the `core:` utility modules. `core:model` holds data classes. `core:designsystem` holds theme tokens and composable wrappers. `:reader-navigator` is a self-contained rendering engine with its own internal architecture. It deserves its own top-level module.

`:reader-navigator` is an Android library module because it owns `StaticLayout`, Canvas rendering, and the `ReaderPageViewer` composable. Its `parser/` package remains Android-free and is covered by JVM unit tests — the Android dependency is confined to `engine/`, `render/`, and `selection/`.

The name is format-agnostic. Today the parser handles EPUB XHTML. If a TXT or HTML parser is added later, it produces the same `ReaderDocument` and feeds the same engine. The module name doesn't change.

### Dependency Graph

```
features:reader:app
  → :reader-navigator (ReaderPageViewer, parser, block model)
  → core:model (ReaderPreferences)
  → core:data (OpenBookUseCase, Publication)
  → core:designsystem (KanshuTheme, for chrome/overlays)
  → core:strings

:reader-navigator
  → core:model (ReaderPreferences — typography input for the viewer)
```

`:reader-navigator` depends only on `core:model` for preference types. It doesn't know about Kavita, Readium, navigation routes, or any feature-level concern. It takes a `ReaderDocument` + preferences and renders pages.

### Feature Module Becomes Thin

`features:reader:app` is a wiring layer:

- **ReaderViewModel** — opens the book via `OpenBookUseCase`, reads XHTML from `Publication`, parses via `EpubParser.parse()` → `ParseResult`, manages chapter sequencing and page state.
- **ReaderScreen** — wraps `ReaderPageViewer` with tap zones, reader chrome overlay, diagnostics panel, and navigation controls.
- **Progress** — persists `ReaderPosition` to Room, syncs with Kavita. See Progress Model.

All rendering, layout, pagination, and selection logic lives in `:reader-navigator`. The feature module never touches `StaticLayout`, `Canvas`, or `BlockStyle`.

## Progress Model

Page count is a navigation convenience, not a persistence primitive. Reflowable EPUB pagination shifts when font size, margins, or viewport change — storing `pageIndex` produces stale resume points.

### Target Shape

```kotlin
@Serializable
data class ReaderPosition(
  val schemaVersion: Int = 2,
  val spineIndex: Int,
  val charOffset: Int,           // character offset into the chapter's flattened text stream
  val progressInSpine: Float,    // charOffset / totalChapterCharLength, 0.0–1.0
)
```

`totalChapterCharLength` is the sum of flattened text character counts across all blocks in the spine item (list prefixes and image placeholders contribute zero). This is intentional: progress offsets must stay stable when Phase 1 adds list prefixes and image blocks — `charOffset` means "offset into the chapter's text stream," not "offset into rendered glyphs on screen." `charOffset` identifies the first visible character on the current page. `progressInSpine` is denormalized for cheap Kavita sync and overall book percentage without re-layout.

### Migration From Schema v1

The codebase currently ships schema v1 with `pageIndex` + `progressInSpine`. Migration is not deferred to Phase 4 — the v2 shape is designed during Phase 0 so page-index assumptions do not spread through the ViewModel.

| When    | Action                                                                                                              |
| ------- | ------------------------------------------------------------------------------------------------------------------- |
| Phase 0 | Define v2 fields and document the mapping. ViewModel may still use page index locally for tap-to-turn.              |
| Phase 4 | Persist v2 to Room, migrate v1 records (best-effort: reopen at spine item start if unmappable), update Kavita sync. |

Kavita's remote progress format may not align with character offsets. Phase 4 defines the mapping layer in `core:data` — local truth is `charOffset`; remote sync uses the best available Kavita field with documented precision loss.

## Migration Path

### Phase 0: Native Rendering and Selection Feasibility Spike

Phase 0 proves the two architectural bets:

1. `StaticLayout` can paginate and render real EPUB text fast enough on the Boox.
2. `StaticLayout` geometry can support native word selection and popup anchoring without WebView.

**Input:**

- One EPUB from the existing book-opening flow.
- First readable spine item.
- XHTML parsed into `ParseResult` (`ReaderDocument` + `ParseDiagnostics`).

**Output:**

- Native paginated reader screen showing real chapter text.
- Paragraph blocks only with basic bold/italic spans (no headings, quotes, lists, images yet). Image resource APIs are deferred until Phase 1+; Phase 0 keeps the engine text-only.
- Next and previous page via tap zones.
- Long-press selects the touched word, highlights it on the Canvas, and shows a placeholder Compose popup anchored near the word.
- Diagnostics panel showing: pagination wall-clock time, per-block p50/p95/max `StaticLayout` construction, Canvas draw time, block count, page count.
- No WebView anywhere in the rendering path.

**Selection scope (intentionally tiny):**

- Long-press selects the touched word.
- The selected word is highlighted on the Canvas (one or more rects for the word, single color). A word may produce multiple rects if hyphenated or wrapped.
- A non-animated Compose placeholder popup appears above or below the word.
- Tapping elsewhere clears the selection.
- Selection only operates inside `ParagraphBlock` entries. Other block types are not present in Phase 0.

**Phase 0 inline span support:**

- Basic bold/italic (`StyleSpan`) only. Real books use italics constantly; without them the reading sample feels fake.
- No font-family spans, link spans, color spans, superscript, or subscript.

**Selection out of scope:**

- Drag handles.
- Multi-word selection.
- Cross-page selection.
- Persistent highlights.
- Notes or dictionary lookup.

This proves the full vertical: touch coordinate → `PageEntry` lookup → `StaticLayout.getLineForVertical` → `getOffsetForHorizontal` → `BreakIterator` word expansion → highlight rect calculation → Canvas drawing → Compose popup anchoring. If this doesn't feel native on the Boox, we know in Phase 0 rather than Phase 3.

**Required deliverables (non-negotiable for Phase 0):**

- Parser unit tests with real EPUB chapter XHTML from the user's Kavita library. The parser is the foundation; it must be tested before anything downstream is built. Tests assert text preservation for tables, asides, and nested divs even when structure is lossy. Tests assert `ParseDiagnostics` tag counts for known unsupported markup.
- `ReaderPosition` schema v2 shape documented and reviewed (implementation may land in Phase 4; design lands in Phase 0).
- Boox EPD API spike: on-device research confirming how to control refresh mode on the Go 7 (Boox SDK / system APIs / `EinkPageTurner` abstraction). Document findings and a Phase 2 integration plan. If no usable API exists, Phase 2 falls back to standard Android surface behavior and EPD work stays in Phase 5.
- On-device profiling data from the Boox Go 7 confirming the < 200ms pagination budget.
- Selection profiling: long-press hit-test time, `BreakIterator` word-boundary time, popup anchor correctness (manual verification on-device).
- Coordinate conversion verification: confirm that selection `Rect` in Compose layout coordinates correctly anchors a popup near the selected word without manual offset adjustments.

**Success criteria:**

Required (Phase 0 fails without these):

- A real chapter is readable on the Boox Go 7.
- First page is readable within 300ms of chapter open.
- Page turns are measurement-free: 0ms layout computation, < 16ms Canvas draw.
- If lazy pagination is used, it stays at least one page ahead of reading.
- Long-press word hit-testing resolves in < 16ms.
- Popup anchor is visually close to the selected word.
- Clearing selection does not trigger page turns or center overlay.
- Parser tests pass with representative EPUB content.

Preferred (target, not hard gate):

- Full chapter eager pagination completes within 200ms.

Diagnostics (recorded for decision-making):

- Unsupported tag counts from `ParseDiagnostics`.
- Per-block `StaticLayout` construction timing (p50/p95/max).
- Max paragraph character count (identifies whether long paragraphs dominate).
- `BreakIterator` word-boundary timing.
- Total eager pagination wall-clock time.

### Phase 1: Full Block Support

Add remaining block types to the parser and renderer:

- Headings with scaled sizes.
- Block quotes with left border.
- Lists with bullet/number prefixes.
- Horizontal rules.
- Inline images (chapter ornaments).

### Phase 2: Typography Preferences and E-ink Baseline

Wire `ReaderPreferences` to the rendering step:

- Font family, font size, line height, alignment, margins.
- Preference changes cancel the in-flight pagination coroutine and launch a new one on `Dispatchers.Default`.
- Debounce preference UI to avoid rapid-fire repagination. At most one layout job in flight.

Introduce baseline Boox EPD integration early — full Canvas redraw on every page turn can trigger aggressive full-panel refresh before typography tuning is done. Scope depends on the Phase 0 EPD API spike:

- If a usable Boox refresh API exists: wire `EinkPageTurner` (or equivalent) for page-turn refresh mode selection.
- If not: document standard Android behavior as baseline; defer Boox-specific control to Phase 5.

Either way, profile ghosting and refresh latency on the Boox Go 7 alongside typography changes during Phase 2. Deeper EPD tuning (per-content-type modes, A2 for fast skim) remains Phase 5.

### Phase 3: Full Selection and Interaction

Building on Phase 0's feasibility proof:

- Drag handles for multi-word selection.
- Cross-page selection (ranges spanning page boundaries).
- Persistent highlights with multiple colors.
- Center tap zone overlay (reader chrome).
- Selection menu with actions (copy, highlight, note).
- Internal links (tap to navigate within book).
- Footnote handling (inline popup or jump-to-note).

### Phase 4: Progress and Navigation

- Implement `ReaderPosition` schema v2 (`charOffset` + `progressInSpine`). Migrate v1 records from Room.
- Progress UI and Kavita sync use character-offset percentage, not page count.
- TOC navigation using `Publication.tableOfContents`.
- Chapter boundary navigation (next/previous spine item).

### Phase 5: E-ink Optimization

- Advanced EPD tuning beyond Phase 2 baseline (content-aware refresh modes, fast-skim A2, profile-specific defaults).
- Same `EinkPageTurner` interface from the Readium migration PRD.
- Profile and tune refresh modes on target hardware after typography and selection are stable.

## Tradeoffs

### What We Gain

- Single text engine for measurement and rendering — zero parity risk.
- Native selection and hit-testing via `StaticLayout` geometry APIs.
- No IPC boundary between Chromium and the app.
- Direct control over every pixel and every screen refresh.
- No JavaScript round-trips for page turns.
- No CSS cascade battles with publisher stylesheets.
- Testable layout engine (instrumented tests with real `StaticLayout`, or JVM tests with a fake `paintFactory`).
- Lower memory footprint (no Chromium renderer process).

### What We Lose

- Complex publisher CSS layouts (multi-column, CSS grid, floats). Acceptable for Kanshu's scope — text books on e-ink don't benefit from these. Kindle constrains similar properties but supports more publisher features than our early renderer will.
- Tables beyond simple grids. Text is preserved via unwrap; column layout is not. Acceptable for early phases.
- Embedded SVG diagrams. Acceptable — rare in reflowable EPUBs; text alt content is preserved when present.
- Publisher-intended visual styling beyond structure. The Kindle model normalizes this rather than stripping it entirely; our approach is more aggressive in Phase 0 but directionally similar.
- Internal links and footnotes as interactive affordances. Deferred to Phase 3. Link text is preserved from Phase 0; tap handling is not.

### What Is Hard

- Pagination. Building `StaticLayout` for every block in a chapter and splitting at the right boundaries. Mitigated by the block model (natural split points) and `StaticLayout`'s low per-call overhead.
- Long paragraphs that span multiple pages. Mitigated by line-level splitting via `StaticLayout.getLineBottom` / `getLineStart`.
- Performance on the Boox SoC. Mitigated by using `StaticLayout` for everything (the lowest overhead path) and profiling in the spike before building further.
- Custom hit-testing for selection. Coordinate-to-character resolution across multiple blocks with varying styles. Mitigated by `StaticLayout`'s native geometry APIs (`getOffsetForHorizontal`, `getLineForVertical`), which are available on every precomputed layout without additional work.
- Canvas rendering of block-level structure. Block quotes with borders, list items with bullets, and clipped split blocks require manual Canvas drawing. Straightforward but more code than Compose composables.
- Hyphenation. `StaticLayout.Builder.setHyphenationFrequency()` is available on API 23+ (including Boox's API 31). Output quality and performance validated in Phase 2.
- CJK / RTL / bidi. Not Phase 0. The block model supports extension (add `writingMode` or `direction` to blocks) without rearchitecting.

## Resolved Decisions

- **`StaticLayout` for both measurement and rendering.** One text engine, zero parity risk. The same `StaticLayout` that determines page splits draws pixels on the Canvas.
- **Block model lives in `:reader-navigator`.** The AST is the native reader engine contract. Parser, layout, rendering, and selection share it inside the same module. `core:model` stays a leaf module for user-facing preferences.
- **Eager pagination is preferred, lazy is acceptable.** Build all `StaticLayout` objects up front on `Dispatchers.Default` if it completes within 200ms. If not, lazy pagination (stay one page ahead of reading) is not a failure — users feel first-page latency and page turns, not whether page 87 was pre-measured. The spike determines which path ships.
- **Parse diagnostics are a parser side-channel.** `EpubParser.parse()` returns `ParseResult(document, diagnostics)`. Unsupported tag counts never appear on the reading surface.
- **Character-offset percentage for progress.** Page count requires full layout and is unstable across preference changes. Character-offset progress (`charOffset / totalChapterCharLength`) is stable, cheap, and matches Kindle's convention. Schema v2 is designed in Phase 0; persistence lands in Phase 4. Text-stream offsets exclude list prefixes and image placeholders so progress survives rendering-phase additions.
- **Silent degradation on the reading surface.** Unsupported markup unwraps to text; tag counts appear in diagnostics only. No placeholder boxes in production.
- **Horizontal insets reduce measurement width.** `prefixWidthPx` and `indentPx` both subtract from layout width before `StaticLayout` construction. Drawing offsets alone are insufficient for lists and quotes.
- **Selection uses `StaticLayout` geometry directly.** No `SelectionContainer`, no `onTextLayout` callbacks. `StaticLayout` exposes line/offset APIs for hit-testing. Full selection is Phase 3; Phase 0 includes one-word selection feasibility only.
- **Cancellation is caller-side, not engine-side.** The layout engine is a pure function. The ViewModel coroutine checks `isActive` between blocks or injects `shouldContinue`. No version counter inside the engine.
- **Android `BreakIterator` runs on-demand, not pre-calculated.** ICU `BreakIterator` is backed by native C++ and expected to be cheap for a single paragraph. Run it at tap time on the touched block's text, not pre-calculated for the whole chapter. Use chapter `xml:lang` when available. Phase 0 records actual timing on-device.
- **Baseline EPD integration in Phase 2.** Typography changes and full Canvas redraws must be profiled with real refresh modes early; advanced tuning stays in Phase 5.
- **Hyphenation uses `StaticLayout` directly.** Bypass Compose's API 33+ limitation by calling `StaticLayout.Builder.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)`. API is available on API 23+ (including Boox Go 7's API 31); output quality and performance validated in Phase 2.
- **Image sizing: intrinsic dimensions capped to column width.** Scale to fit `viewport.widthPx - horizontalMargins`, preserving aspect ratio. Small ornaments stay small; large images fill the available width without clipping. Failed bounds lookup uses a fixed placeholder height, not zero-height skip.
- **Ares `:htmlparser` is reference only.** Borrow parser traversal patterns (`InlineStyleExtractor`, fallback text preservation, tag handler registry); do not depend on Ares's Compose scroll renderer.

## Relationship to Other Docs

- `docs/PRD.md` — project north star. This PRD is the reader engine strategy underneath Phase 0's "render enough EPUB content to actually read."
- `docs/KINDLE_TYPOGRAPHY.md` — the layout-mine-fonts-yours model. The native engine implements this directly: the block model carries publisher structure, the renderer applies Kanshu typography.
- `docs/PRD_READIUM_MIGRATION.md` — the previous WebView-based migration plan. Superseded by this PRD. The Readium streamer/shared retention, sanitization allowlist research, Boox EPD integration, and `EinkPageTurner` interface carry forward. The WebView renderer, CSS pagination, JS bridge, request interceptor, and command serialization do not.
- `docs/READIUM_API.md` — Readium navigator surface reference. No longer needed for the reader engine (navigator is removed). Still useful as historical context for why certain APIs don't exist.
- `docs/READIUM_PAGINATION_EXTRACTION.md` — Readium pagination source analysis. Historical context only. The native engine does not use CSS multi-column pagination.
- Ares `../ares/htmlparser` — sibling RSS app's HTML parser. Reference for inline style extraction and fallback traversal; not a Kanshu dependency. See § Reference: Ares `:htmlparser`.
