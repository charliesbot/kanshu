# Publisher Styles Engine

**Date:** 2026-07-11

## TL;DR

Kanshu resolves a deliberately small CSS cascade while parsing each EPUB spine item, then stores
only structural results on the native reader's block and span model. Publisher emphasis,
alignment, spacing, indentation, and selected heading structure survive; reader preferences still
own legibility typography. CSS never reaches pagination or page turns, and unsupported input
degrades to semantic-markup rendering with diagnostic counts.

## Design

### Ownership boundary

Publisher markup and styles describe the book's structure. Kanshu owns the typography needed for a
consistent reading surface.

| Owner | Signals |
| --- | --- |
| Reader | Font family, body font size and weight, line height, page margins, default justification, word spacing, letter spacing, additive paragraph spacing |
| Publisher | Semantic blocks, emphasis, block alignment, vertical rhythm, first-line indentation, block insets, and the admitted stacked-heading subset |

A centered scene break or indented letter is structural and may override the reader's default
alignment or spacing. A publisher body font, color, or arbitrary font size is not. Publisher
signals refine the block model; they never introduce a second typography system.

### Pipeline placement

```text
Spine XHTML
  +-> linked stylesheets -> ReaderResourceLoader -> CssParser cache
  +-> <style> blocks and inline declarations
  -> Jsoup DOM walk + StyleResolver
  -> ReaderDocument with resolved structural values
  -> ReaderLayoutEngine -> ReaderPage -> Canvas
```

Stylesheet hrefs resolve relative to the spine item. Parsed external sheets are cached per
publication by resolved href because chapters normally share them. Inline `style` declarations and
document `<style>` blocks enter the same rule model. Resolution occurs during the existing DOM walk
and disappears at the `ParseResult` boundary.

Nothing in pagination, rendering, hit-testing, selection, or page-turn handling parses CSS,
matches selectors, or reads class names. This is a hard performance and module boundary.

### Parsing and selector scope

`ph-css` provides fault-tolerant CSS tokenization and grammar handling behind a Kanshu adapter.
The adapter produces the small internal rule model and owns selector admission, specificity,
property admission, normalization, and diagnostics. This keeps uncontrolled EPUB input out of a
home-grown tokenizer without delegating product decisions to a browser engine.

Supported selectors are type, class, ID, compounds, descendant chains, and selector lists. Child
and sibling combinators, attribute selectors, pseudo-classes and pseudo-elements, media queries,
and font-face rules are ignored and counted. Inline declarations outrank rules; otherwise standard
ID/class/type specificity and source order decide. `!important` is not honored.

Inherited properties flow down the DOM walk. Margins and `display` are resolved only where
declared because they do not inherit. A resolved value uses `null` to mean “no publisher signal,”
allowing `BlockStyleResolver` to apply the reader default without guessing why a value is absent.

### Admitted property surface

The current allowlist is intentionally structural:

| Property | Accepted meaning | Model result |
| --- | --- | --- |
| `font-style` | italic, oblique, or normal | Inline italic bit |
| `font-weight` | bold/bolder, numeric threshold, or normal | Inline bold bit |
| `text-align` | start, end, center, or justify | Optional block alignment |
| `margin-top`, `margin-bottom`, `margin` | Vertical rhythm | Block spacing |
| `text-indent` | First-line indentation | Block spacing |
| `margin-left`, `margin-right`, `margin` | Structural block inset | Block spacing |
| `display: block | inline` | Stacked descendants inside semantic headings only | Heading component breaks |

Semantic emphasis and CSS emphasis merge into the same inline-style lattice, so downstream layout
cannot distinguish `<em>` from an equivalent class. Publisher alignment is optional; absence keeps
the reader's justification default. `text-align: justify` therefore collapses to the default,
while explicit start, end, or center alignment preserves structural intent.

`display` is not a general box-layout feature. It is admitted only for inline descendants of
`h1`–`h6`, covering accessible headings whose number and title are separate block-styled spans.
The same declaration under paragraphs, lists, quotes, or generic containers remains diagnostic
only because supporting it there would require CSS anonymous-box semantics.

### Structural spacing normalization

Publisher spacing is converted to em during parsing. `em` and `rem` remain ratios, points use
12pt per em, and pixels use 16px per em. Vertical margins clamp to `0..2em`, first-line indentation
to `0..3em`, and cumulative horizontal insets to `0..6em`. Percentages, negative values, `auto`,
and unparseable lengths produce no signal.

These clamps preserve publisher rhythm without allowing fixed desktop-oriented values to destroy
an e-ink viewport. Headings honor publisher margins and insets but not first-line indentation.
Margins do not inherit; `text-indent` does.

For a book with no publisher spacing signals, body paragraphs use the Kindle-style fallback:
first-line indentation with no artificial vertical gap. The reader's paragraph-spacing preference
is additive over publisher or fallback rhythm and defaults to zero.

### Diagnostics and property admission

`ParseDiagnostics.stylingCensus` records class and inline-style usage, declarations by property,
selector shapes, at-rules, unsupported selectors, important declarations, stylesheet hrefs and
sizes, and block-styled heading descendants. The reader diagnostics surface exposes this evidence
without changing the page.

The allowlist grows only when representative library data demonstrates a visible structural loss.
Admission requires a model mapping, normalization rules, degradation behavior, and proof that the
signal resolves entirely before pagination. Convenience, isolated fixtures, or familiar CSS names
are insufficient.

Current candidates include small caps, superscript/subscript, underline, and narrowly scoped
relative sizing for headings or front matter. Publisher color, body font family or size, floats,
table layout, positioning, and font-face loading remain outside the model. If corpus needs begin to
require those layout semantics, the premise of a micro-cascade must be reconsidered rather than
expanded property by property into a browser.

### Failure behavior

Styling is never allowed to make a readable book fail. An unreadable or malformed sheet contributes
nothing. Oversized sheets are skipped at the resource boundary. Unsupported selectors,
declarations, properties, and at-rules are ignored independently and counted. Semantic XHTML
continues through the parser in every case.

Page turns pay no styling cost. External sheets parse once per publication; element resolution is
part of the off-main-thread DOM walk. If style processing becomes a meaningful part of first-page
latency, the implementation skips the problematic input rather than moving resolution later.

### Engine boundary and escape hatches

Kotlin remains the orchestration layer because shaping, line breaking, hyphenation, font fallback,
and drawing already execute in Android's native text and graphics stacks through `StaticLayout`
and Canvas. Moving the micro-cascade to native code would add a JNI boundary without replacing the
actual hot path.

This decision reopens only if profiling shows Kotlin style resolution itself dominates the chapter
budget, the required property surface demands true layout semantics such as floats or tables, or
typography requirements exceed Android's text stack after its available controls are exhausted.
The preferred escape hatch is embedding a mature engine such as crengine behind the
`:reader-navigator` boundary, not incrementally rebuilding a browser in Kotlin.

## Open questions

- Does representative corpus data justify following one level of CSS `@import`?
- Which candidate property produces the next highest-value structural fidelity improvement?
- Is a reader-facing “publisher styles” toggle useful enough to expose, or should graceful
  normalization remain unconditional?

## Links

- [Native Reader Engine](native-reader.md)
- [Kindle CSS Application Model](../research/kindle-typography.md)
- [Kanshu PRD](../PRD.md)
- [Scribe stacked-title guidance](https://scribenet.com/wfdw/updates/2025/december-01-digital-hub-update.html)
