# Kavita API — Reference for Kanshu

Practical reference for the slice of the Kavita API that Kanshu uses. Captures the decisions and gotchas we've already paid for so we don't rediscover them.

## Source of truth

- OpenAPI spec: <https://raw.githubusercontent.com/Kareadita/Kavita/develop/openapi.json> — authoritative; verify here when this doc and behavior disagree.
- Wiki: <https://wiki.kavitareader.com/guides/api/>

When updating this doc, search the OpenAPI spec by path string rather than line number — the spec drifts.

## Authentication

The OpenAPI spec declares one global security scheme: `AuthKey`, an API key in the `x-api-key` header. Almost every endpoint inherits it.

```
GET /api/Server/server-info-slim
x-api-key: <user api key>
```

Kavita derives the user from the key, so user-scoped endpoints (reader progress, account) work with `x-api-key` alone.

### When you actually need the JWT

`POST /api/Plugin/authenticate?apiKey=<key>&pluginName=<name>` exchanges the API key for a JWT, returning a `UserDto` whose `token` field is used as `Authorization: Bearer <jwt>`.

**Rule of thumb: `x-api-key` is enough for everything Kanshu does.** The JWT exchange is only needed for SignalR hubs and OPDS-style endpoints that don't accept the key. None of those are on the Kanshu roadmap.

### Image endpoints are different

Image endpoints take the API key as a **query param**, not a header — so `<img src>` works without setting headers. See cover images below.

## Endpoints we use

### Test connection — `GET /api/Server/server-info-slim`

Lightweight, auth-gated. Returns 200 with a small JSON payload on valid creds, 401 on bad key. The right call for a "test connection" button.

**Do not use `/api/Health` for auth tests** — it's an unauthenticated Docker liveness probe, returns 200 regardless of credentials, will silently false-positive a wrong key.

Always check `Content-Type: application/json` before trusting the body. A misconfigured base URL (user typed the SPA root instead of the API root, or the wrong port) can return the SPA HTML with HTTP 200.

### List libraries — `GET /api/Library/libraries`

Returns `LibraryDto[]` — the libraries the calling user can see. Use this to scope series queries by `libraryId`.

**Do not use `/api/Library/list`** — that's a filesystem directory picker for the admin UI, not user libraries.

### List series — `POST /api/Series/all-v2`

Body: `SeriesFilterV2Dto` (use `{}` for unfiltered). Query: `PageNumber`, `PageSize`, optional `userId`, `context`. Returns `SeriesDto[]`.

`/api/Series/all` (no `-v2`) **does not exist** in the current spec despite older docs/PRDs referencing it. Always prefer `-v2` paths — many older `/api/Series/*` routes are deprecated but still in the spec.

To restrict to EPUB only, filter `SeriesFilterV2Dto.statements` by `format == 3` (`MangaFormat.Epub` is an int enum, not a string). Filtering by `libraryId` is usually cleaner if the user has dedicated EPUB libraries.

`SeriesDto` key fields: `id`, `name`, `originalName`, `localizedName`, `pages`, `pagesRead`, `format`, `libraryId`, `libraryName`, `coverImage`.

**`coverImage` is an opaque token, not a URL.** Use the cover endpoint below to render it.

**Pagination metadata lives in a response header, not the body.** Read the `Pagination-Header` HTTP header — it contains JSON with `currentPage`, `itemsPerPage`, `totalItems`, `totalPages`. The OpenAPI spec doesn't document this.

### Cover images — `GET /api/Image/series-cover?seriesId={id}&apiKey={key}`

API key goes in the **query string** here, not the header — so the URL is directly usable as an `<img src>`. Same pattern for `/api/Image/chapter-cover` and friends.

### Download an EPUB

Kavita's model is Series → Volume → Chapter → file(s). For EPUB libraries, each book is typically one chapter with one `.epub` file.

1. `GET /api/Series/volumes?seriesId={id}` → `VolumeDto[]`, each with its `chapters` populated.
2. Pick the chapter id.
3. `GET /api/Download/chapter?chapterId={id}` → the file bytes.

**The response is not always an EPUB.** If the chapter has a single file, you get the raw `.epub` with `Content-Type: application/epub+zip`. If it has multiple files, Kavita zips them and returns `application/zip`. Always inspect `Content-Type` and use the filename from `Content-Disposition`; do not assume the extension from the URL.

Optional pre-flight: `GET /api/Download/chapter-size?chapterId={id}` returns the byte size. `/api/Download/volume` and `/api/Download/series` exist for bulk downloads.

**Do not use `/api/Reader/*` to fetch the file** — those endpoints are for paginated server-side reading (`/api/Reader/pdf`, `/api/Reader/image` stream individual pages, `/api/Reader/chapter-info` returns reader metadata). Wrong tool for an offline reader; we want the file on the device.

### `ChapterDto` field warning

`number` on `ChapterDto` is deprecated. Use `minNumber`, `maxNumber`, or `sortOrder`.

## Reading progress sync — `/api/Reader/progress` (POST) and `/api/Reader/get-progress` (GET)

POST body and GET response are both `ProgressDto`:

```
ProgressDto {
  volumeId      : int        // required
  chapterId     : int        // required
  pageNum       : int        // required — Kavita's server-side chapter page index
  seriesId      : int        // required
  libraryId     : int        // required
  bookScrollId  : string?    // EPUB anchor id for in-page position
  lastModifiedUtc : date-time
}
```

GET takes `?chapterId=`. Auxiliary: `/api/Reader/has-progress?seriesId=` returns `bool`; `/api/Reader/first-progress-date?userId=` returns a timestamp.

**Locator format mismatch — read this before touching sync code.** Kavita stores `pageNum + bookScrollId`, not a Readium locator. Kanshu's reader emits Readium `Locator`s. Pushing up means deriving a best-effort `pageNum` (from `totalProgression`) and a `bookScrollId` (from the nearest id'd element to the user's position in the rendered DOM). Pulling down means looking up the element by `bookScrollId` and reconstructing a `Locator`. The conversion is structurally lossy:

- Kavita's `pageNum` depends on Kavita's render width/font — it's not portable across renderers. Treat it as a hint, not a truth.
- `bookScrollId` precision depends on the EPUB's id authoring (paragraph-level ids → good; chapter-level ids → land at the top of the section).
- `progression` (0..1) is the only field that round-trips cleanly between renderers — use it as the comparable when implementing conflict resolution.

Local source of truth on Kanshu is the Readium locator. The Kavita-shaped projection (chapterId, seriesId, volumeId, libraryId, pageNum, bookScrollId) is written by the Kavita provider into `reading_progress.sync_metadata` — an opaque JSON column the schema does not interpret. Pulling from Kavita is best-effort: apply the locator only if we can resolve `bookScrollId`; otherwise keep the local position. The `sync_metadata` column is deliberately schemaless so a future provider can store its own shape without a migration — see `ReadingProgressEntity` for the rationale.

## Annotations (highlights + notes) — `/api/Annotation/*`

Kavita supports text annotations as a first-class API. **Highlights and notes are the same entity** — an annotation is always anchored to a selection (xPath is required) and optionally carries a comment.

Endpoints:

- `POST /api/Annotation/create` — body `AnnotationDto`, returns the created `AnnotationDto`
- `POST /api/Annotation/update` — modify spoiler flag, highlight slot, and comment fields
- `DELETE /api/Annotation?annotationId=` — delete
- `GET /api/Annotation/{id}` — by id
- `GET /api/Annotation/all?chapterId=` — list for a chapter
- `GET /api/Annotation/all-for-series?seriesId=` — list across a series

Plus filter/export/like endpoints we don't use.

`AnnotationDto` key fields:

```
xPath, endingXPath   : string    // DOM range, REQUIRED — Kavita uses XPath not Readium locators
selectedText         : string    // the highlighted text
comment              : string?   // the note body (and commentHtml/commentPlainText variants)
selectedSlotIndex    : int       // color slot
containsSpoiler      : bool
pageNumber           : int       // chapter page where the annotation lives
chapterId/volumeId/
seriesId/libraryId   : int       // required, scoping
ownerUserId          : int       // server-derived from the api key
createdUtc           : date-time
```

**Cannot model "note without a highlight"** — `xPath` is required server-side. If we want local-only freeform notes, that's a separate construct that can't sync to Kavita.

Same xPath-vs-Readium-locator mismatch as progress. The conversion (Readium `Locator.locations.domRange` ↔ XPath start/end) is mechanical DOM traversal but real work; lands in the sync layer, not the data layer.

## Endpoints we don't use yet

Listed so we don't re-evaluate them every time.

- **`POST /api/Plugin/authenticate`** — JWT exchange. Not needed; `x-api-key` covers our endpoints.
- **`GET /api/Plugin/version?apiKey=`** — legacy plugin handshake. Logs unauthorized hits to the security log; don't probe it for connection tests.
- **`/api/Reader/*` (streaming)** — server-side paginated reading (`/api/Reader/pdf`, `/api/Reader/image`, `getBookPage`). Kanshu renders EPUBs locally with Readium, so we download the file and skip these. Inkita uses this pipeline; we explicitly don't because it breaks the offline-first and source-agnostic goals in the PRD.
- **`/api/Reader/mark-*`** — mark read/unread. Useful for batch ops post-Phase-0.
- **`/api/Reader/*-bookmarks`** — Kavita's bookmark feature is page-image bookmarks for manga/comics, not EPUB text annotations. Wrong tool for our use case.
- **`POST /api/Series/v2`** — per-library filtered listing. Useful later if we add library-scoped views; for now `all-v2` is enough.
- **`/api/Koreader/{apiKey}/syncs/progress`** — KOReader sync compatibility shim. Uses `ebookHash` as identity and a simpler payload. We have full access to the native endpoints; no reason to use the shim.
- **`/api/Health`** — unauthenticated Docker liveness probe. Useless for our purposes; documented above as a footgun.

## Footguns reference

Quick scannable list of the gotchas already covered above:

- `/api/Series/all` does not exist — use `/api/Series/all-v2`.
- `/api/Library/list` is the filesystem dir picker, not user libraries — use `/api/Library/libraries`.
- `/api/Health` is unauthenticated — useless for credential validation.
- Misconfigured base URL can return SPA HTML with HTTP 200 — check `Content-Type` on responses.
- Pagination metadata is in the `Pagination-Header` response header, not the body.
- `coverImage` on `SeriesDto` is a token, not a URL — go through `/api/Image/series-cover`.
- Image endpoints take `apiKey` as a query param, not a header.
- `MangaFormat` is an int enum in JSON (`3` = EPUB), not a string.
- `Download/chapter` returns `application/epub+zip` for single-file chapters and `application/zip` for multi-file ones — inspect `Content-Type`.
- `ChapterDto.number` is deprecated — use `minNumber` / `maxNumber` / `sortOrder`.
- Prefer `-v2` paths; non-v2 variants are often deprecated.
- Kavita stores progress as `pageNum + bookScrollId` (its own pagination) and annotations as XPath — neither is wire-compatible with Readium locators. Conversion is lossy; treat `progression` (0..1) as the only comparable that round-trips cleanly.
- `AnnotationDto.xPath` is required — there is no way to sync a freeform "note without a highlight" to Kavita.
- `/api/Reader/bookmark*` is for manga page-image bookmarks, not EPUB text — don't confuse it with `/api/Annotation/*`.
