# Native Reader Engine

**Date:** 2026-05-25

## TL;DR

Kanshu renders reflowable EPUB content with Android `StaticLayout` and Canvas rather than a
WebView. Readium remains responsible for EPUB container and publication I/O; `:reader-navigator`
owns parsing, the block model, pagination, rendering, hit-testing, and selection. The same
precomputed `StaticLayout` artifacts measure and draw each page, so page turns perform no text
measurement and reader preferences cannot create measurement/rendering drift.

## Design

### Boundaries

The engine targets general reflowable EPUBs on the Boox Go 7 Gen 2 B&W. Fixed-layout books,
manga, browser-grade CSS layout, and exact rendering of tables, floats, columns, or absolute
positioning are outside this design. Unsupported markup must degrade to preserved text rather
than crash or add visible error chrome.

The app-wide e-ink rules remain in force: no animated page transitions, ripples, fades, or
crossfades, and all interactive targets remain at least 48dp. Compose owns reader chrome,
settings, popups, and navigation; Canvas owns the book page.

Kanshu retains `readium-shared` and `readium-streamer` for publication opening, spine ordering,
resource resolution, and reading-order metadata. It does not use `readium-navigator` or a WebView
in the rendering path.

### Pipeline

```text
EPUB file
  -> Readium Publication
  -> reading-order XHTML and resources
  -> Jsoup parser
  -> ReaderDocument + ParseDiagnostics
  -> ReaderLayoutEngine (StaticLayout + TextPaint)
  -> precomputed ReaderPage entries
  -> ReaderPageViewer / Canvas
```

Publisher CSS is resolved during parsing, before the block model reaches pagination. The
micro-cascade, supported properties, and publisher-versus-reader ownership rules are defined in
[Publisher Styles Engine](publisher-styles-engine.md). Nothing downstream of `ParseResult`
parses stylesheets, matches selectors, or inspects class names.

### Document model and degradation

`ReaderDocument` is an inspectable block tree containing paragraphs, headings, quotes, lists,
horizontal rules, and images. Inline spans preserve nested emphasis and links until layout
flattens them into the `Spannable` consumed by `StaticLayout`. The document also carries its
language when the EPUB declares one so word-boundary selection can choose an appropriate locale.

Parser output includes `ParseDiagnostics` as a side channel. Unsupported block and inline tags
are counted, styling census data records unhandled publisher signals, and the reading surface
stays silent. Unknown elements unwrap their children into the nearest valid block so readable
text is never discarded merely because its structure is unsupported.

Image references resolve relative to the spine item at parse time. Pagination reads image headers
without decoding full bitmaps, scales intrinsic dimensions to the content column, and reserves the
resulting height. Bitmap decoding happens off the UI thread. A target page is presented only after
its images decode, fail, or hit a short timeout; adjacent pages are decoded ahead. Missing or
corrupt images use a fixed placeholder so late image work never reflows text.

### Layout and pagination

Each text-bearing block receives a complete `BlockStyle`: an immutable `TextPaint`, line spacing,
alignment, hyphenation, horizontal insets, prefix gutter, and vertical margins. The style resolver
builds fresh paint instances for each layout job. Once a `StaticLayout` has been constructed, its
paint is treated as immutable and ownership transfers with the completed pages to rendering.

The layout engine creates one `StaticLayout` per block and accumulates block heights into pages.
Adjacent vertical margins collapse using the larger value. Horizontal inset and prefix widths
reduce the measurement width rather than merely shifting drawing coordinates. Long blocks reuse
one layout across `SplitBlock` page entries; each entry stores its visible line range and cached
vertical offsets.

The same `StaticLayout` is both the measurement and drawing artifact. This establishes three hard
invariants:

- Page turns swap an index and draw prebuilt layouts; they never construct or measure text.
- The renderer draws one current page and does not maintain off-screen Canvas surfaces.
- Preference or viewport changes cancel the old layout job and create an entirely new immutable
  page set.

Eager full-chapter pagination is preferred when it stays within the target-device latency budget.
The compatible fallback is incremental pagination that remains ahead of the reader; it does not
change rendering or progress semantics. Layout work runs away from the UI thread, and cancellation
belongs to the caller rather than the pure layout engine.

### Rendering

`ReaderPageViewer` hosts an Android Canvas view from Compose and draws the current page's
`PageEntry` objects. Text and headings call `StaticLayout.draw()`. Quotes and lists draw their
border or marker inside the prefix gutter, images draw cached bitmaps into their pagination-time
bounds, and rules draw directly across the content column.

Reader preferences own font family, size, weight, line height, page margins, default alignment,
word and letter spacing, and additive paragraph spacing. Publisher signals may refine structural
emphasis, alignment, vertical rhythm, first-line indentation, and block insets, but cannot replace
reader-owned typography.

### Interaction geometry

Selection, links, and highlights use `StaticLayout` geometry rather than Compose
`SelectionContainer`. A touch first resolves to the page entry and local layout coordinates, then
uses `getLineForVertical()` and `getOffsetForHorizontal()` to locate a character. Word selection
expands that offset with `BreakIterator`; highlight rectangles come from line bounds and character
horizontals on the same layout.

Selection positions are stable text ranges, not pixels:

```kotlin
data class TextPosition(
  val blockIndex: Int,
  val offset: Int,
)
```

Link spans carry inert EPUB href metadata inside the `Spannable`. A tap resolves its character
offset through the same geometry pipeline and queries for the metadata span. Feature-layer code
then routes same-chapter fragments, cross-chapter links, and footnotes without delegating touch
handling to a text widget or browser.

Persistent highlights use flattened-text offsets and are drawn against layout geometry. Selection
may continue across pages within the current spine item; cross-spine ownership remains above
`ReaderPageViewer` because changing spine items replaces its document. Source element paths used to
translate those offsets for provider synchronization are defined in
[Highlight Persistence and Synchronization](highlight-persistence-and-sync.md).

### Progress identity

Page indexes are transient navigation state because pagination changes with preferences and
viewport size. Durable positions use the spine index and character offset into the exact flattened
text stream that feeds `StaticLayout`, with a denormalized progression for comparison and remote
sync.

Any change to whitespace normalization, tag promotion, or other flattening behavior can invalidate
stored offsets. Such a change therefore requires an explicit position-compatibility decision; it
must not be treated as an internal parser refactor. List markers and image placeholders do not
contribute to the text stream, keeping offsets stable as non-text rendering evolves.

### Module ownership

`:reader-navigator` is a top-level Android library. It owns the block model, XHTML parser, layout
engine, Canvas renderer, image handling, selection, links, and highlight geometry. Its parser
package remains plain Kotlin/JVM where possible, while layout and rendering use Android APIs.

The module depends only on `:core:model` for `ReaderPreferences`. It does not know about Kavita,
providers, Readium `Publication`, app navigation, Room, or reader-screen state. The reader feature
opens publications, loads resources, parses spine items, sequences chapters and pages, persists
progress, and wraps `ReaderPageViewer` with chrome and overlays.

### Performance constraints

Page-turn work is limited to state selection, bitmap readiness, and drawing precomputed artifacts.
Chapter parsing, CSS resolution, image-bounds lookup, and pagination occur before presentation and
off the UI thread. Diagnostics retain block counts, unsupported-tag and styling census data,
layout timing, page count, and Canvas draw timing so target-device regressions remain observable.

If representative chapters exceed the first-page budget, diagnosis proceeds from pathological
blocks to long-block splitting and only then to incremental pagination. The single-text-engine and
measurement-free page-turn invariants do not change.

## Open questions

- Does full-chapter eager pagination remain preferable on the Boox Go 7 across the representative
  library, or should incremental pagination become the default?
- Which Boox refresh API and mode provide the best balance of latency and ghosting for Canvas page
  swaps without hidden-API coupling?
- What compatibility mechanism should accompany the first unavoidable change to flattened-text
  offset semantics?

## Links

- [Kanshu PRD](../PRD.md)
- [Publisher Styles Engine](publisher-styles-engine.md)
- [Highlight Persistence and Synchronization](highlight-persistence-and-sync.md)
- [Kindle CSS Application Model](../research/kindle-typography.md)
- [Boox SDK research](../research/boox-sdk.md)
