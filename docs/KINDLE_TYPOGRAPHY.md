# Kindle CSS Application Model

Kindle's "Enhanced Typesetting" (KFX) engine is the de facto reference for reflowable ebook typography. It does **not** evaluate publisher CSS live the way a browser does, nor does it ignore publisher CSS and substitute its own. The pipeline pre-bakes the EPUB through a headless render, extracts computed styles, normalizes problematic patterns, and then layers user preferences over the result. The takeaway: publisher CSS is the _starting point_, not the final word.

## 1. The Core Architecture: KFX & Ion

KFX (Kindle Format 10) is a proprietary binary format built on Amazon's **Ion** — a binary-encoded superset of JSON. Unlike legacy EPUB/MOBI rendering, KFX shifts from a live-render model to a **pre-baked** model.

### Pre-Baking & Normalization

When an EPUB is converted to KFX (typically inside Kindle Previewer or KDP's upload pipeline), the engine:

- **Headless render at a reference width.** The book is rendered in a headless WebKit (PhantomJS in older versions of the toolchain) at a reference viewport. The commonly cited width is ~512px.
- **Computed style extraction.** For every element, the engine reads the _computed_ style — the post-cascade values — and serializes them with the content.
- **Decomposition into Ion fragments.** The HTML+CSS is broken down into Ion records: content nodes with their resolved styles already attached. The final book file ships these baked fragments, not raw stylesheets.

## 2. Normalization Heuristics

KFX's normalization pass rewrites or constrains a small set of publisher CSS patterns that historically break reflowable reading. **Important correction over older drafts of this doc:** Amazon's KF8 CSS reference lists most "dangerous" properties as _supported_, not stripped. The engine's normalization is narrower and more nuanced than "blanket strip":

- **`position`.** `position: absolute` and `position: fixed` are documented as supported but "not recommended" for reflowable layouts; they're inconsistently honored across devices and are commonly the source of disappearing or overlapping text. Treat them as fragile, not forbidden.
- **`float`.** Supported, but in practice some `float` declarations are coerced to `display: inline` during normalization, especially around inline images.
- **`width` / `height`.** Supported on containers — but `max-width` and `max-height` are explicitly **not** supported. This is a real gap and affects responsive image sizing.
- **`color` / `background-color`.** Supported on most elements. The engine doesn't blanket-strip these, but Kindle's theme system (sepia, dark, black) overrides them at render time when a non-default theme is active.
- **Unit handling.** Absolute and relative units are normalized through the computed-style extraction. There is community evidence that `text-indent` is recomputed into a constrained range (publisher reports of 5em indents being "corrected" downward), but no primary Amazon documentation specifies an exact range.

The honest summary: KFX **constrains** publisher CSS through computed-style extraction and theme overrides; it does not categorically delete properties.

## 3. User-Agent Defaults

KFX applies a baseline stylesheet for elements publishers commonly under-specify. Documented examples:

- **Full justification** is the default for body text and is forced regardless of publisher CSS, unless the user has changed alignment in their settings.
- **List padding** on `<ul>` / `<ol>` is enforced so bullets are never cut off — publisher overrides on list padding are unreliable.
- Line height, heading margins, and indentation also have engine defaults, but the specific numeric ranges sometimes cited (e.g. line-height 1.2–1.4) are not in primary Amazon documentation.

## 4. Enhanced Typesetting (Modern Renderer)

The current rendering engine (Amazon brands user-facing features under "Enhanced Typesetting") adds:

- **Language-aware hyphenation.** Soft hyphens are inserted dynamically using language dictionaries.
- **Adaptive justification.** When line length shrinks (large font sizes, narrow viewports), the engine can switch from full justify to left-align to avoid awkward word gaps. This behavior is observed and documented by third parties; Amazon describes it in user-facing terms as "improved word spacing."
- **OpenType kerning and ligatures.** GPOS/GSUB features are honored where the font provides them.

## 5. The Layout vs. Legibility & Spacing Split

The conceptual separation that makes Kindle's output consistent across publishers:

- **Layout (structural CSS).** Headings, blockquotes, signatures, paragraph indents, list structure — preserved from publisher CSS because they define the _shape_ of the book.
- **Legibility & Spacing (typography CSS).** Typeface, font size, line height, page margins, alignment, paragraph spacing, and word/letter spacing — owned by the user. The "Publisher Font" toggle in the Kindle UI controls whether the publisher's `font-family` is even applied; with it off, the engine uses Bookerly or another Kindle font. Size, spacing, and line height are always user-driven and take precedence over publisher CSS.

This split is the operative model for a Kindle-quality reader: don't try to honor everything the publisher said about typography and spacing, and don't try to override everything the publisher said about structure.

## 6. Cover Pages — Open Question

Cover rendering is **not** part of the documented CSS pipeline. Amazon's KDP guidelines instruct _publishers_ to ship cover XHTML that fills the viewport (commonly using `<svg viewBox preserveAspectRatio>` to get `object-fit: contain` semantics across devices, since `max-width`/`max-height` are unsupported). There is no primary source documenting an automatic Kindle-side override that makes any cover XHTML fill the screen. Books that render edge-to-edge on Kindle do so because the publisher used the SVG-wrapper pattern; books that letterbox do so because the publisher didn't. KDP also discourages an HTML cover page in addition to the cover image, suggesting Kindle sometimes synthesizes its own cover surface from OPF metadata rather than rendering the publisher's cover XHTML.

For Kanshu: don't expect Kindle parity on covers by passing publisher cover CSS through Readium unchanged. If we want consistent viewport-fill rendering, we have to implement it ourselves — exactly the same conclusion KFX's pipeline reached internally.

## Summary for Implementation

To replicate Kindle's quality, a reader should move away from "render the publisher CSS as-is" and toward **opinionated normalization**:

1. **Honor structural CSS** (headings, blockquotes, indents, list structure).
2. **Inject robust defaults** for things publishers commonly miss (justification, list padding, heading margins).
3. **Constrain** known-fragile properties (`position: absolute`, hardcoded widths that don't account for the viewport).
4. **Override legibility & spacing CSS** with user preferences (font family, font size, line height, margins, alignment, paragraph spacing, and word/letter spacing).
5. **Treat cover pages as a separate concern** — don't assume publisher CSS will fill the viewport.

## References

Verified primary and community sources used to back the claims above.

### Amazon / KDP (primary)

- **KF8 CSS reference** — what KFX nominally supports and rejects: <https://kdp.amazon.com/en_US/help/topic/GG5R7N649LECKP7U>
- **Enhanced Typesetting** — user-facing feature description: <https://kdp.amazon.com/en_US/help/topic/G202087570>
- **Publisher Font toggle / user font controls**: <https://kdp.amazon.com/en_US/help/topic/GH4DRT75GWWAGBTU>
- **Image and cover guidelines**: <https://kdp.amazon.com/en_US/help/topic/G75V4YX5X8GRGXWV>

### Community / technical writeups

- **KFX format overview (MobileRead Wiki)** — Ion format, structural details: <https://wiki.mobileread.com/wiki/KFX>
- **"What Kindle does behind the scenes" — Jiminy Panoz** — pre-baked rendering, computed style extraction, 512px reference: <https://medium.com/@jiminypan/what-kindle-does-behind-the-scenes-3d1be22efce3>
- **FriendsOfEpub `WillThatBeOverriden` — Kindle Previewer 3 / KFX notes** — independent confirmation of computed-style extraction and dynamic H&J: <https://github.com/FriendsOfEpub/WillThatBeOverriden/blob/master/ReadingSystems/Kindle/KDF-KFX/KindlePreviewer3.md>
- **MobileRead thread on text-indent normalization (#321918)** — publisher report of indent recomputation: <https://www.mobileread.com/forums/showthread.php?t=321918>
