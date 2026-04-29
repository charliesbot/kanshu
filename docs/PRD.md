# PRD: Kanshu

## What Is This?

Kanshu is a minimal Android ebook reader for e-ink tablets, starting with one real setup: a Boox Go 7 Gen 2 B&W linked to a Kavita server.

The first version is not trying to be a general-purpose reader. It is a personal reading surface for opening books from Kavita and reading them in a quiet, high-contrast interface built around e-ink constraints.

The product philosophy is simple: the book is king. Reading mode should show the book, not the app.

## North Star

Kanshu should feel like the reader that should have shipped with the device: fast, quiet, high-contrast, hardware-aware, and deeply boring in the best way.

The long-term goal is a distraction-free bridge between a personal Kavita library and an e-ink tablet. It should avoid the clutter and visual noise of general Android readers while still becoming flexible enough to support local books later.

## Research

- Target test device: Boox Go 7 Gen 2 B&W.
- Primary source: Kavita server through the Kavita API.
- Kavita API notes:
  - Auth can use an `x-api-key` header.
  - Swagger may be available at `/swagger/index.html`.
  - Useful early endpoint candidates:
    - `GET /api/Series/all` for library discovery.
    - Reader/download endpoints for resolving and downloading EPUB files.
- E-ink constraints:
  - No animations, ripples, fades, slides, page curls, or animated indicators.
  - Prefer instant state cuts.
  - Prefer pure black and pure white over gray-heavy UI.
  - Use borders instead of shadows or elevation.
  - Keep persistent UI out of the reader to reduce ghosting and distraction.
- Reader UX references:
  - Kindle: make the current read easy to resume.
  - Kobo: respect typography, margins, and font weight.
  - Boox NeoReader: respect hardware buttons and e-ink refresh behavior.
  - Lithium-style Android readers: keep the library simple and low-bloat.
- Android stack direction:
  - Jetpack Compose.
  - compose-unstyled from the start.
  - Navigation 3 with transitions disabled.
  - Ktor for Kavita networking.
  - A source/repository boundary so the UI consumes books, not Kavita-specific objects.
- EPUB rendering:
  - Readium is the serious long-term EPUB engine candidate.
  - A simpler temporary renderer may be acceptable only if Readium blocks the Phase 0 steel thread.
- Future source support:
  - Local EPUB open and local folder sources are valuable, but not the early roadmap priority.
  - Local folders will likely need Android Storage Access Framework.
  - The reader should eventually open a book without caring whether it came from Kavita, a local file, or another source.

## Phase 0: Canvas

Phase 0 is the smallest real version for the actual setup: Boox Go 7 Gen 2 B&W plus one Kavita server.

It should prove that Kanshu can be a personal reading surface, not just a demo.

Must have:

- A minimal Kavita setup surface:
  - base URL
  - API key
  - save
  - test or use connection
- Fetch a real library list from Kavita using the API.
- Show a minimal high-contrast unstyled list of readable items.
- Select one book and download/open its EPUB.
- Render enough EPUB content to actually read.
- Use Compose and compose-unstyled as the UI foundation.
- Use Navigation 3 with instant transitions.
- Enforce the no-animation rule from the first UI:
  - no ripples
  - no fades
  - no slides
  - no page-turn animation
  - no animated progress indicators
  - no elevation or shadows
- Reading mode defaults to zero persistent app UI.
- The reader uses generous margins and a black-on-white reading surface.
- Kavita is implemented as the first source behind a source/repository boundary.
- Physical Boox page button handling for page turns.
- Top tap zone reveals a minimal reader overlay.
- Side tap zones turn pages.
- Immersive reader mode hides system bars.

## Later

- Proper source management for Kavita connection details.
- High-contrast cover grid.
- Current-read prominence inspired by Kindle.
- Zoned reader taps:
  - top zone toggles reader overlay
  - side zones turn pages
  - center remains text-first
- Physical Boox page buttons mapped to page turns.
- Font size, font weight, and margin controls inspired by Kobo.
- Boox SDK exploration for refresh behavior if standard Android behavior is not enough.
- Kavita progress sync.
- Offline cache and downloaded-book management.
- Local EPUB open.
- Local folder source through Android Storage Access Framework.
- Mandarin learning ideas such as pinyin or dictionary lookup.
