# Kindle CSS Application Model

Kindle's "Enhanced Typesetting" (KFX) engine represents the gold standard for reflowable ebook typography. It achieves its polished look through an aggressive, multi-stage CSS transformation process that prioritizes readability over raw source fidelity.

## 1. The Core Architecture: KFX & Ion
Kindle Format 10 (KFX) is a proprietary binary format based on Amazon's **Ion** superset of JSON. This architecture marks a shift from a "live rendering" model (like a browser) to a "pre-baked" model.

### Pre-Baking & Normalization
When an EPUB is converted to KFX, the engine does not simply "copy" the CSS. Instead:
- **Headless Rendering:** The book is rendered in a headless WebKit environment at a reference width (typically 512px).
- **Computed Style Extraction:** The engine extracts the **computed styles** for every element. Absolute pixel values are preferred over relative units at this stage.
- **Decomposition:** The HTML and CSS are decomposed into thousands of **Ion fragments**. These fragments represent content blocks with hardcoded, normalized styling rules already attached.

## 2. The Normalization Heuristics
Kindle "nails" the styling by aggressively cleaning up "bad" EPUB CSS during the conversion process.

### Property Stripping
The engine strips or ignores properties that interfere with a reflowable, high-contrast reading environment:
- **`position: absolute/fixed`**: Stripped to prevent text from overlapping or disappearing.
- **`float`**: Heavily restricted; allowed only for simple image-wrapping.
- **`color` & `background-color`**: Stripped on body/paragraph text to ensure WCAG-compliant contrast and to prevent breaking "Night Mode" or "Sepia" themes.
- **`width` & `height`**: Stripped from containers (`div`, `p`, `body`) to ensure the text always fits the physical screen.

### Unit Normalization
Kindle converts chaotic units into a standardized internal grid:
- **Absolute to Relative**: `px` or `pt` values are normalized into `em` equivalents or forced to the device default if they are too close to standard body text size.
- **Indentation Scaling**: Paragraph indents (`text-indent`) are normalized to a standard range (typically 1.5em to 2em). If a publisher sets a 5em indent, the engine may "correct" it to 1.5em to preserve page balance.

## 3. The "User Agent" Defaults
Kindle applies a "virtual" stylesheet that provides robust defaults for elements the publisher might have ignored:
- **`text-align: justify`**: The default for long-form reading on most Kindles.
- **`line-height: 1.2 - 1.4`**: A comfortable baseline that the user can further scale.
- **`margin-top/bottom`**: Applied to headings (`h1`-`h6`) even if missing in the source, ensuring a clear visual hierarchy.
- **List Padding**: Enforced `padding-left` on `<ul>` and `<ol>` to ensure bullets are never cut off.

## 4. Enhanced Typesetting (ET) Features
The **YJ Engine** (the modern Kindle renderer) applies advanced typography that standard EPUB engines often miss:
- **Advanced H&J (Hyphenation & Justification)**: Uses language-specific dictionaries to insert soft hyphens dynamically, preventing "rivers" of white space.
- **Intelligent Justification**: If the line length is too short (due to large font size), the engine dynamically switches from full justification to left-alignment to prevent awkward word gaps.
- **Kerning & Ligatures**: Native support for OpenType features (`GPOS`/`GSUB`), ensuring professional character spacing.
- **Responsive Drop Caps**: Drop caps are treated as special objects that scale and nestle into the surrounding text accurately across all font sizes.

## 5. The "Layout vs. Fonts" Split
Kindle enforces a strict conceptual separation that is the secret to its consistency:
- **Layout (Structural)**: Headings, blockquotes, signatures, and initial indents. These are preserved from the source CSS as they define the book's *structure*.
- **Fonts (Legibility)**: Typeface, size, line spacing, and justification. These are entirely owned by the user. The engine treats the publisher's `font-family` and `font-size` on body text as merely "suggestions" that are overridden the moment the user touches the settings menu.

## Summary for Implementation
To replicate Kindle's quality, a reader must move away from "rendering the CSS as-is" and toward an **Opinionated Normalization** model:
1. **Strip** problematic layout properties (positioning, hardcoded colors).
2. **Inject** robust defaults for indentation and spacing.
3. **Normalize** units to a relative `em` grid.
4. Override legibility properties (font-family, size, line-height) with user preferences.

## References

- **Kindle KFX Architecture & Ion Format**: [MobileRead Forum - KFX Input/Output Plugins](https://www.mobileread.com/forums/showthread.php?t=263902)
- **Enhanced Typesetting (YJ Engine) Internals**: [JustKindleBooks - Enhanced Typesetting Features](https://www.justkindlebooks.com/blog/kindle-enhanced-typesetting/)
- **Amazon Kindle Publishing Guidelines**: [Official PDF - Section 3: Formatting Guidelines](https://kdp.amazon.com/en_US/help/topic/G200645680)
- **Kindle CSS Normalization Heuristics**: [Reddit - KFX Normalization Details](https://www.reddit.com/r/kindle/comments/16l5w5h/technical_details_of_kfx_normalization/)
- **KFX vs. KF8 Comparison**: [The Ebook Reader - Kindle Formats Explained](https://blog.the-ebook-reader.com/2017/05/17/kindle-formats-explained-mobi-azw-azw3-kfx/)
- **Advanced Hyphenation & Justification**: [MobileRead - KFX Typography Research](https://www.mobileread.com/forums/showthread.php?t=291040)
- **CSS Property Stripping in Kindle**: [Dodeca - Kindle CSS Override Logic](https://dodeca.co.uk/blog/kindle-typography-settings/)

