# PRD: Publisher Styles Engine

## What Is This?

Kanshu's native reader currently renders structure from semantic XHTML tags only (`<em>`, `<strong>`, `<h1>`, `<blockquote>`). A large fraction of real EPUBs — especially Calibre conversions and InDesign exports — express emphasis, alignment, and structure exclusively through CSS classes and stylesheets:

```html
<p class="calibre3">It was <span class="calibre7">not</span> a good idea.</p>
<p class="center">* * *</p>
```

where `calibre7` maps to `font-style: italic` and `center` to `text-align: center` in a stylesheet Kanshu never opens. To the current parser these are an unknown span to unwrap and a plain paragraph. The book has italics and centered scene breaks; Kanshu throws away the only place they are expressed.

This PRD defines how Kanshu consumes publisher CSS without becoming a browser: a **micro-cascade** — honest CSS cascade semantics over a deliberately tiny property set, resolved once per chapter at parse time.

It supersedes the "Phase 2.5: Data-Gated CSS Signals" section of `docs/PRD_NATIVE_READER.md`, which underestimated how much of the real corpus speaks CSS-only. The observed evidence: the same book opened side by side shows italics, bold, and centered text on Kindle and undifferentiated plain text in Kanshu. That is not a corner case; it is the common case.

## Why This Is a Product Priority

Kanshu's goal is to be the best e-reader for Android e-ink devices: KOReader's rendering quality, Kindle's frictionlessness, delivered as a real Android app instead of an OS inside an OS. Rendering fidelity is the product. A reader that drops the publisher's italics is not a spartan reader — it is a broken one. Progress sync, TOC, and annotations only matter once the page itself is worth returning to, which is why the roadmap now orders publisher styles ahead of progress persistence (see § Roadmap Reordering).

## North Star

Open the same reflowable book in Kindle and in Kanshu. The pages should be structurally indistinguishable: emphasis lands where the publisher put it, scene breaks are centered, chapter openers keep their alignment. Meanwhile the reader's typography ownership is untouched — font family, size, line height, margins, and justification default remain Kanshu preferences — and styling adds **zero** cost to pagination or page turns.

## Constraints

Inherited from `docs/PRD_NATIVE_READER.md` and unchanged:

- Target device: Boox Go 7 Gen 2 B&W (Android 12 / API 31).
- Page turns never trigger measurement, layout construction, or style resolution.
- Corpus: general reflowable EPUBs from the user's Kavita library. No manga, no fixed-layout.
- Silent degradation: styling failures never crash or produce visible chrome; they fall back to semantic-tags-only rendering and are counted in diagnostics.

New for this PRD:

- **CSS work is parse-time only.** Stylesheets are parsed once per publication and cached; per-element resolution happens during the existing DOM walk, off the UI thread. Nothing downstream of `ParseResult` knows CSS exists.
- **The parser package stays JVM-pure.** The CSS tokenizer, rule model, selector matcher, and cascade live in `:reader-navigator`'s `parser/` package with plain-JVM unit tests, like the XHTML parser.
- **Property growth is data-gated.** The allowlist grows only when the styling census (§ Diagnostics) shows a property earning its place in the actual library.

## Prior Art

### KOReader / crengine

KOReader's EPUB path is crengine, a C++ engine inherited from CoolReader and extended for ~15 years. It is a small special-purpose browser: persistent DOM, full stylesheet parsing, genuine selector matching with specificity and source order, inheritance, and a broad property surface that grew to include floats, tables, `::before/::after`, and counters. It ships its own default stylesheets, lets users toggle publisher styles wholesale, and layers user CSS tweaks into the cascade.

Two lessons:

1. **What made crengine correct is cascade semantics, not property breadth.** Real publisher CSS uses descendant selectors, multiple classes, and conflicting rules where specificity decides. Engines that match flat class selectors with last-rule-wins render some books right and others subtly wrong in ways that are miserable to debug.
2. **What made KOReader feel alien is everything else** — its own rasterizer, font stack, and UI world. The fidelity and the alienness come from the same architectural decision (bring your own engine), which was forced by platforms that have no text stack. Android is not such a platform.

### Kindle (KF8)

Kindle's renderer is a native CSS-subset engine: it honors a curated slice of publisher CSS for structure and emphasis while clamping user-owned typography on top. This is the ownership split Kanshu already adopted from `docs/KINDLE_TYPOGRAPHY.md` — Kanshu simply never implemented the publisher half. Kindle is the fidelity bar for this PRD: not TeX, not InDesign — Kindle.

### Engine Language Decision

Considered and resolved: Kanshu stays Kotlin. The reasoning, recorded so it is a falsifiable bet rather than dogma:

- Kanshu's hot path is already C++. `StaticLayout` is a shell over Minikin (HarfBuzz shaping, optimal line breaking, hyphenation, font fallback, bidi); drawing is Skia; image decode and `BreakIterator` are native. Kotlin orchestrates; the hot loops never run in Kotlin. Page turns are zero computation by hard rule.
- A micro-cascade over a chapter DOM (hundreds of elements, hundreds of rules, once per chapter, off the main thread) is sub-millisecond work in any language. Crengine's CSS advantage is fifteen years of semantics, not native speed.
- A Rust/C++ engine would evict Minikin/Skia and obligate reimplementing shaping, fallback, bidi, selection geometry, and pagination behind a JNI boundary — the same flavor of bridge the WebView removal just eliminated. There is no NDK API into Minikin; the platform text stack is reachable only from the JVM side. The Onyx EPD SDK is Java. For a one-person project, that maintenance surface dwarfs the product.

**Tripwires that reopen this decision:**

1. On-device profiling shows chapter styling + pagination blowing the 200ms budget with the Kotlin work itself dominating (not I/O, not Minikin).
2. The data-gated property list starts demanding real layout semantics — floats, tables-as-layout, absolute positioning — i.e., Kanshu is rebuilding a browser in Kotlin.
3. Typography ambitions exceed Minikin's knobs after genuinely exhausting them (hanging punctuation, custom justification, per-language hyphenation dictionaries beyond the system's).

If a tripwire fires, the escape hatch is not a rewrite: it is **embedding crengine itself** (an embeddable C++ library — exactly how KOReader consumes it) behind the existing `ReaderDocument` contract, inheriting the fifteen years instead of re-serving them. The `:reader-navigator` module boundary makes this substitution possible without touching the feature layer.

## Ownership Model

Extends the Kindle-style split from `docs/KINDLE_TYPOGRAPHY.md`:

| Owner     | Properties                                                                                                                                                                                              |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Reader    | Font family, font size, line height, page margins, justification default, word spacing, letter spacing. Paragraph spacing becomes an **additive** preference over publisher rhythm (see § Structural Spacing). |
| Publisher | Structural semantics (headings, quotes, lists, emphasis) **plus, via this PRD:** emphasis, block alignment, and structural spacing (vertical margins, first-line indent, block insets) expressed in CSS.  |

Publisher signals refine structure; they never override reader typography. `text-align: center` on a scene break wins over the justification default because it is structural intent; `margin-top` on a copyright paragraph wins because vertical rhythm is structural intent; `font-size: 0.9em` on body text loses because size is reader-owned.

A user-visible **publisher styles toggle** (KOReader's idea) is a candidate future product feature once the engine exists; it is one boolean at parse time. Not in scope for the first slices.

## Architecture

### Pipeline Placement

```
Publication spine item (XHTML)
  |
  +-- <head><link rel="stylesheet"> hrefs ──> ReaderResourceLoader ──> CSS bytes
  |                                             |
  |                                   CssParser (once per stylesheet, cached per publication)
  |                                             |
  v                                             v
Jsoup DOM walk  ──────────────────────  StyleResolver (micro-cascade)
  |                                             |
  v                                             v
ReaderDocument blocks with resolved InlineStyle + block alignment
  |
  v
(unchanged) ReaderLayoutEngine -> ReaderPage -> Canvas
```

Everything downstream of `ParseResult` is untouched. Pagination, page turns, hit-testing, and selection never see CSS.

### Stylesheet Discovery and Caching

- Each spine item declares its stylesheets via `<link rel="stylesheet" href>` in `<head>` (and rarely `<style>` blocks). Hrefs resolve against the spine item path with the existing `resolveHref`.
- Stylesheet bytes load through the existing `ReaderResourceLoader`; parsed stylesheets are cached per publication keyed by resolved href — chapters overwhelmingly share one or two sheets, so the parse cost is paid once per book.
- `@import` is not followed in v1; occurrences are counted in diagnostics. If the census shows real usage, following one level is a small addition.
- Inline `style=""` attributes are parsed as bare declaration lists during the DOM walk.

### CSS Parser Scope

Tokenization and grammar are outsourced to **ph-css** (`com.helger:ph-css`, Apache 2.0) in browser-compliant fault-tolerant mode, behind an adapter that produces Kanshu's `CssStylesheet` model. The rationale (revisited from the original hand-rolled plan): parsing is the commodity layer over an **unbounded, uncontrolled input surface** — every stylesheet in every EPUB ever produced — which is exactly where home-grown scanners bleed indefinitely (silent misparse on strings-containing-braces, escapes, encodings). ph-css is a decade of other people's messy CSS encoded as code. The adapter keeps every Kanshu decision: which selector shapes the cascade honors, the property allowlist, and the census counts for everything skipped; unparseable input degrades to an empty stylesheet inside the same never-throw boundary.

Alternatives surveyed and recorded: **KSS** (Kotlin, MIT, spec CSS Syntax L3, featherweight but single-maintainer-young) is the drop-in fallback behind the same adapter if ph-css's dex footprint ever proves unacceptable on the e-ink build; **jStyleParser** is disqualified (LGPL v3 vs. Android distribution, `org.w3c.dom` impedance vs. our Jsoup walk, stagnant mainline); **css4j** (BSD-2, active, computed-styles engine) is the graded escape hatch *before* embedding crengine if Kanshu ever wants publisher styles wholesale rather than an allowlist.

Supported selector grammar (v1):

| Shape                | Example                  |
| -------------------- | ------------------------ |
| Type                 | `p`                      |
| Class                | `.center`                |
| ID                   | `#dedication`            |
| Compound             | `p.calibre3`, `.a.b`     |
| Descendant           | `p.dedication span`      |
| Selector lists       | `i, em, .italic`         |

Not supported in v1, counted in diagnostics when seen: child/sibling combinators (`>`, `+`, `~`), attribute selectors, pseudo-classes/elements, `@media`, `@font-face` (reader owns fonts), shorthand `font:`.

### Cascade Semantics

The part borrowed from crengine, implemented honestly:

- **Matching:** an element matches a rule if the rightmost compound matches it and remaining compounds match ancestors in order (descendant semantics).
- **Precedence:** inline `style` > higher specificity > later source order. Specificity is the standard (id, class, type) triple. No `!important` in v1 (counted; Calibre emits it rarely).
- **Inheritance:** resolved values inherit down the DOM walk for inheritable properties (all of v1's properties inherit per CSS). The walk already descends recursively; inheritance is passing the parent's resolved style down.

Resolution produces one small immutable value per element:

```kotlin
internal data class ResolvedStyle(
  val italic: Boolean? = null,      // font-style
  val bold: Boolean? = null,        // font-weight >= 600 | bold / normal
  val alignment: BlockAlignment? = null, // text-align
)

enum class BlockAlignment { Start, Center, End }
```

`null` means "no publisher signal — reader default." Values fold into the existing model at emission time: `italic`/`bold` merge into the same `InlineStyle` lattice the semantic tags use (a `<span class="italic">` becomes indistinguishable from `<em>`); `alignment` lands on a new optional field on text-bearing blocks:

```kotlin
data class ParagraphBlock(
  val spans: List<TextSpan>,
  val alignment: BlockAlignment? = null, // null -> reader's justification default
) : ReaderBlock
// HeadingBlock gains the same field.
```

`BlockStyleResolver` maps a non-null alignment to the corresponding `Layout.Alignment` (and disables justification for centered/end-aligned blocks); a null keeps today's behavior exactly.

### Degradation Policy

Same posture as the XHTML parser: styling never crashes and never produces visible chrome.

- Malformed or unreadable stylesheet → that sheet contributes nothing; the chapter renders from semantic tags as today. Counted.
- Oversized stylesheet (cap: 256KB per sheet) → skipped, counted. Real book sheets are a few KB.
- Unknown properties, unsupported selectors, unparseable declarations → skipped individually, counted by name/shape.

## Property Surface

### v1 Allowlist

| Property      | Values honored                                   | Maps to                    |
| ------------- | ------------------------------------------------ | -------------------------- |
| `font-style`  | `italic`, `oblique`, `normal`                    | `InlineStyle` italic bit   |
| `font-weight` | `bold`, `bolder`, numeric ≥ 600 / `normal`, < 600 | `InlineStyle` bold bit     |
| `text-align`  | `center`, `right`/`end`, `left`/`start`, `justify`* | Block `alignment`          |

\* `text-align: justify` maps to null (the reader's default already justifies); `left` maps to `Start` only when the reader default is justify — it is the publisher opting a block out of justification (poetry, code), which is structural intent.

### v2 Allowlist: Structural Spacing (census-admitted July 2026)

The census did its job. Evidence from a representative InDesign export (Hachette, `idGeneratedStyles.css`, single sheet shared by all 49 spine items): **77 `margin-top`, 75 `margin-bottom`, 73 `text-indent`, 62 `margin-left`, 60 `margin-right`, 19 `margin` shorthand** declarations — against zero honored. On-device Kindle side-by-side (July 2026) showed the consequences per page:

- **Copyright page:** paragraph classes split into spaced (`CRTS`, `margin-top`) and glued (`CRT`, margin 0) variants. Kindle renders the publisher's rhythm — grouped address blocks, separated legal paragraphs. Kanshu flattened both into one uniform gap.
- **Chapter bodies:** body text (`TX`) separates by first-line indent with zero vertical margin; the chapter opener (`COTX`) is the unindented exception. Kindle shows the classic indent convention; Kanshu showed no indents and a uniform gap the publisher never asked for.

A single reader-side spacing constant cannot reproduce either page — the spacing *is* per-paragraph publisher data. This admits the structural spacing set:

| Property                                          | Maps to                                                | Renders via                                  |
| ------------------------------------------------- | ------------------------------------------------------ | --------------------------------------------- |
| `margin-top`, `margin-bottom` (+ shorthand vertical components) | Block vertical rhythm                                   | Existing `BlockStyle.marginTop/BottomPx`      |
| `text-indent`                                     | First-line indent                                       | `StaticLayout.Builder.setIndents` (first line indented, remaining lines 0) |
| `margin-left`, `margin-right` (+ shorthand components) | Block insets (quotes, verse, letters without `<blockquote>`) | Existing `indentPx` inset model               |

**Normalization is the core design piece** (the actual Kindle behavior — honor, then clamp): units resolve to em at parse time (`em`/`rem` as-is, `pt` ÷ 12, `px` ÷ 16); vertical margins clamp to `0..2em`, `text-indent` to `0..3em`, horizontal insets to `0..6em` cumulative. Percentages (width-relative, not font-relative), negative values, `auto`, and unparseable lengths degrade to "no signal," never break pagination. Resolved values are stored on blocks at parse time like `BlockAlignment` — nothing downstream learns CSS exists. Headings honor publisher margins and insets but not `text-indent` (indented headings are not a book convention worth the surface).

Cascade note: unlike every v1 property, **margins do not inherit** in CSS — they resolve declared-only (the `resolveDeclared` path), while `text-indent` inherits normally. The inheritance table in § Cascade Semantics is v1-specific.

**Unstyled-book defaults (product decision, ships with the `text-indent` slice):** books with no spacing signals get Kindle's fallback convention — first-line indent on body paragraphs, no vertical gap — replacing today's flat gap. The reader's paragraph-spacing slider becomes additive on top of publisher/default rhythm, defaulting to 0. This supersedes the flat per-block-type spacing table in `docs/PRD_NATIVE_READER.md` § Block Spacing Model for blocks carrying publisher spacing.

### v3 Candidates (admitted only by census data)

- `font-variant: small-caps` — `InlineStyle.SmallCaps` already exists, unused.
- `vertical-align: super/sub` + `<sub>/<sup>` styling — currently unwrapped to plain text.
- `text-decoration: underline` — rare in books outside links.
- Relative `font-size` on headings/front matter — deferred indefinitely; touches pagination budgets for marginal gain.

### Never (tripwire territory, not backlog)

Color (B&W panel), publisher `font-family`/`font-size` on body text (reader-owned), floats, tables-as-layout, absolute/relative positioning, `@font-face`.

## Diagnostics: the Styling Census

Phase A ships measurement before any rendering change, extending `ParseDiagnostics`:

- Count of elements carrying `class` / `style` attributes; distinct class names on structural elements.
- Declarations seen per property name (allowlisted and not), across stylesheets and inline styles.
- Selector shapes seen vs. matched vs. unsupported.
- Would-match counts: how many elements would receive each v1 property if resolution were on.
- Stylesheet stats: count, size, parse time, `@import`/`!important`/`@media` occurrences.

Surfaced in the existing diagnostics panel. Running this across the Kavita library validates the v1 list and is the standing admission mechanism for v2 — replacing PRD_NATIVE_READER's vaguer "measure residual CSS reliance" with a concrete instrument.

## Performance

Budgets extend the native reader PRD's table; all measured on the Boox Go 7:

| Operation                        | Budget                | Notes                                                                                  |
| -------------------------------- | --------------------- | --------------------------------------------------------------------------------------- |
| Stylesheet parse (per book)      | < 50ms, once, cached  | Off UI thread during first chapter open; sheets are KB-scale.                           |
| Per-chapter style resolution     | Inside existing < 300ms first-page budget | Folded into the DOM walk; expected low single-digit ms.              |
| Pagination / page turn           | +0ms                  | Unchanged by hard rule; styles are baked into blocks before layout sees them.           |

If a pathological book blows the stylesheet budget, the degradation policy applies: skip the sheet, count it, render semantic-only.

## Slices

**A — Styling census.** Diagnostics extension + panel display. No rendering change. Deliverable: numbers from real library books, v1 list confirmed or amended.

**B — CSS parser + micro-cascade, pure JVM.** ph-css adapter producing the rule model, selector matching, specificity, inheritance, `ResolvedStyle`. Tested against real stylesheet fixtures from the census books (Calibre and InDesign output), including specificity conflicts, descendant matching, and a malformed-input never-throws corpus at the adapter boundary. No wiring.

**C — Wire-up.** Stylesheet discovery/caching through `ReaderResourceLoader`, resolution in the DOM walk, `InlineStyle` merge, block `alignment` field, `BlockStyleResolver` mapping. The slice where books visibly change. On-device Kindle side-by-side is the acceptance test.

**D+ — Data-gated growth.** One property per slice, census-justified, each with fixtures.

Structural spacing (§ v2 Allowlist) lands as three D-series slices, in this order:

**E — Publisher vertical margins.** `margin-top`/`margin-bottom` (+ shorthand vertical components) through the cascade onto blocks; `BlockStyleResolver` prefers publisher values over the reader constant. Acceptance: the copyright-page rhythm matches the Kindle side-by-side.

**F — `text-indent` + unstyled-book defaults.** First-line indent via `StaticLayout.Builder.setIndents`; Kindle-convention fallback for unstyled books (indent, no gap); paragraph-spacing slider becomes additive, default 0. Acceptance: chapter bodies separate by indent, not uniform gaps.

**G — Horizontal insets.** `margin-left`/`margin-right` onto the `indentPx` model with the cumulative clamp. Acceptance: indented verse/letters render inset without breaking measurement-width parity.

## Success Criteria

- Books that express emphasis via CSS classes show italics and bold identically to their semantic-tag equivalents.
- Centered scene breaks and chapter openers render centered.
- A semantic-tags-only book renders byte-identically to today (regression fixture).
- Malformed-CSS book renders as today, with counts in the panel.
- Census numbers recorded for the library; pagination and page-turn timings unchanged on-device.

After the structural spacing slices (E–G):

- The copyright-page vertical rhythm (spaced vs. glued paragraph groups) matches the Kindle side-by-side.
- Chapter body paragraphs separate by first-line indent with no artificial vertical gap, matching Kindle's default look.
- An unstyled book renders with the Kindle fallback convention (indent, no gap), not a flat uniform gap.

## Tradeoffs

**Gained:** the visible fidelity gap against Kindle closes for the dominant real-world EPUB shape; cascade correctness means conflicts resolve the way the publisher intended; the census turns future fidelity arguments into data.

**Deliberately not gained:** browser-grade layout. Floats, tables, columns remain unwrapped text. Publisher typography beyond emphasis/alignment stays overridden — that is the product's ownership model, now made explicit rather than accidental.

**Risk:** selector matching against ancestors adds parse-time cost per element. Mitigated by the tiny rule counts of real books, per-publication sheet caching, and the census measuring parse time before slice C ships.

## Roadmap Reordering

This PRD is first in the product-first ordering agreed for completing the native reader (supersedes the phase ordering tail of `docs/PRD_NATIVE_READER.md`):

1. **Publisher styles** (this PRD, slices A–C).
2. **EPD spike + baseline refresh control** — requires on-device work; typography judgments are polluted by ghosting until refresh modes are controlled.
3. **Typography quality pass** — Kindle side-by-side defect list: hyphenation validation, justification quality, widow/orphan rules. Own discussion/PRD. (The paragraph model — first-line indent vs. spacing — moved into this PRD as § Structural Spacing, slices E–F.)
4. **Structural fidelity by census rank** (slices D+, sub/sup, small caps).
5. **Links and footnotes** (PRD_NATIVE_READER Phase 3 remainder).
6. **Progress, TOC, Kavita sync — last**, once pagination behavior has settled and a resume point is stable.

## Relationship to Other Docs

- `docs/PRD_NATIVE_READER.md` — the engine this builds on. This PRD supersedes its "Phase 2.5: Data-Gated CSS Signals" section and its phase-ordering tail (see § Roadmap Reordering). Its hard rules (page-turn purity, parse-time-only style work, silent degradation) are inherited unchanged.
- `docs/KINDLE_TYPOGRAPHY.md` — the ownership split this PRD implements the publisher half of.
- `docs/PRD.md` — project north star; "best e-reader for Android e-ink" framing.
- KOReader/crengine — prior art only (`lvstsheet.cpp` is the stylesheet engine to consult for semantics questions); the embed option is the documented tripwire escape hatch, not a dependency.
