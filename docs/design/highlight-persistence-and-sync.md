# Highlight Persistence and Synchronization

**Date:** 2026-08-30

## TL;DR

Kanshu stores highlights as exact character ranges in the native reader's flattened text stream and
renders directly from those ranges. Source element paths exist only to translate highlights at a
provider seam: Kavita receives XPath plus selected text, while highlights pulled from Kavita are
resolved back into Kanshu offsets. Local changes are immediate and durable; provider synchronization
is asynchronous, retryable, and deliberately avoids content-repair machinery.

## Design

### Local identity and anchor

A highlight belongs to one Kanshu book and one EPUB spine resource. Its canonical location remains
the half-open `[startCharOffset, endCharOffset)` range in the flattened text stream produced by
`:reader-navigator`. This is the same coordinate system used by selection, pagination, and reading
progress, so font, margin, line-spacing, and page-count changes do not move the highlight.

The persisted annotation contains:

```kotlin
data class ReaderAnnotation(
  val id: String,
  val bookId: BookId,
  val spineIndex: Int,
  val startCharOffset: Int,
  val endCharOffset: Int,
  val selectedText: String,
  val startElementPath: SourceElementPath,
  val endElementPath: SourceElementPath,
  val color: ReaderHighlightColor,
  val createdAt: Long,
  val updatedAt: Long,
  val remoteId: String?,
  val syncState: HighlightSyncState,
)
```

`id` is Kanshu's stable local identity. `remoteId` is the owning provider's identity for the same
annotation and is null until a remote annotation exists. Because every book has exactly one owning
provider, Kanshu does not need a separate collection of provider replicas for one highlight.

`selectedText` is display and translation data, not the normal local locator. Local rendering never
searches for it. `spineIndex` plus the character range remains authoritative inside Kanshu.

Highlights are limited to one EPUB spine resource. A selection may cross any number of rendered
pages within that resource, but does not cross into another XHTML resource.

### Saving and rendering

Creating, recoloring, or deleting a highlight writes Room before contacting a provider. Room's flow
for the current book and spine resource updates the reader, and Canvas redraws the visible
intersection of each stored range with the current page. Rendering performs no XPath evaluation,
text search, provider access, or network work.

The three synchronization states are:

```kotlin
enum class HighlightSyncState {
  SYNCED,
  PENDING_UPSERT,
  PENDING_DELETE,
}
```

A create or recolor sets `PENDING_UPSERT`. A delete sets `PENDING_DELETE`; reader queries exclude
that row immediately, but retain it as a tombstone until the provider acknowledges deletion. After
acknowledgement, the row can be physically removed. Providers without highlight synchronization
leave local highlights fully functional and do not require pending work.

### Source element paths

Provider translation requires the relationship between flattened characters and the source XHTML
elements that produced them. During parsing, `:reader-navigator` records a provider-neutral source
element path alongside the canonical range contributed by each text span. Selection can therefore
return both exact stream offsets and the source elements containing its start and end.

Source element paths are translation metadata, not a second canonical anchor and not a repair
mechanism. A provider adapter formats them into its wire representation. Kavita formats them as
XPath; another provider can use a different representation without changing the persisted local
offset semantics.

`SourceElementPath` is a zero-based list of element-child indexes relative to the XHTML `<body>`:

```kotlin
data class SourceElementPath(val childIndexes: List<Int>)
```

Each step indexes `Element.children()`, so indentation text and comments do not affect the path. The
path contains neither tag names nor provider syntax. Resolving it produces the original XHTML
element; the Kavita adapter then derives Kavita's one-based, same-tag-sibling XPath. An incoming
Kavita XPath is resolved to an element first, then converted to the same child-index path. Room
stores the integer list directly through ordinary serialization rather than a custom packed format.

The same mapping supports the reverse direction: a provider adapter resolves its wire anchor to a
source element range, and `:reader-navigator` maps a selected-text match within that range to
flattened-text offsets. Provider adapters must not independently reimplement Kanshu's whitespace
normalization because two flattening implementations would eventually disagree about offsets.

### Synchronization

Synchronization runs asynchronously after a local mutation and after a book opens. It does not
delay the first locally available page. Failed work remains in its pending state and is retried on a
later trigger.

One synchronization round for a book performs:

```text
pending deletes
  -> pending creates and updates
  -> complete remote pull
  -> local import and refresh by remote ID
```

Successful remote creation stores the returned `remoteId` and marks the row `SYNCED`. Successful
update marks the row `SYNCED`. Successful deletion removes the tombstone. A complete successful pull
imports remote-only highlights, updates already linked `SYNCED` highlights, and removes linked local
highlights that no longer exist remotely.

This first design does not implement a general conflict engine. Pending local changes are pushed
before pulling, so an intentional unsynchronized local change wins that round. Otherwise, the
provider's current representation updates the local annotation. Independent annotations are never
merged merely because they contain the same text or offsets.

The owning provider receives the acquired book context and the source mapping needed for anchor
translation. Provider-specific book identifiers required by annotation endpoints remain opaque
provider metadata; shared annotation code does not interpret Kavita series, volume, chapter, or
library IDs.

### Kavita interoperability

Kavita annotations are scoped to the authenticated user and contain a starting XPath, ending XPath,
selected text, EPUB reading-order page number, color-slot index, and Kavita book identifiers. Kanshu
creates and updates annotations through `/api/Annotation/*` using the same API-key identity as the
configured provider. A read-only Kavita user cannot create, update, or delete annotations.

For a Kanshu-created highlight, the Kavita adapter sends:

```text
source element paths -> XPath and ending XPath
spine resource       -> pageNumber
selected text        -> selectedText
Kanshu color         -> selectedSlotIndex [0-4]
provider metadata    -> chapter, volume, series, and library IDs
```

When the same user opens the book in Kavita's web reader, Kavita loads the annotation and injects an
`<app-epub-highlight>` element into the served EPUB HTML. The annotation therefore appears as a
normal Kavita highlight and in Kavita's annotation views. A page already open in the browser may
need to be reloaded or revisited because synchronization does not push into an already rendered web
page.

For a Kavita-created highlight, Kanshu fetches the annotation, identifies the spine resource from
`pageNumber`, resolves its XPath range against the original XHTML, and searches for `selectedText`
inside that bounded element range after applying Kavita-compatible whitespace normalization. The
resulting position is converted to Kanshu's flattened-text offsets, saved in Room with Kavita's ID
as `remoteId`, and rendered normally thereafter.

This reverse conversion uses literal text matching, not a regular expression. Kavita's own renderer
also uses the first literal match of normalized `selectedText` inside the addressed element range.
If identical selected text occurs more than once in that range, Kavita may already display the
first occurrence rather than the originally selected occurrence. Kanshu follows the same first-match
behavior so both readers agree. If the XPath cannot be resolved or the selected text is absent,
Kanshu skips the remote annotation rather than highlighting unrelated text.

Kavita stores a color slot rather than an RGB value. The mapping is private to `KavitaProvider`:

```text
Aqua   <-> slot 0
Green  <-> slot 1
Yellow <-> slot 2
Orange <-> slot 3
Pink   <-> slot 4
```

This follows Kavita's default cyan, green, yellow, orange, and magenta slot order. Shared annotation
code and `:reader-navigator` know only `ReaderHighlightColor`; every provider owns translation to
its wire representation. The mapping is explicit rather than based on Kotlin enum ordinals. Because
Kavita users can customize slot colors, the exact displayed web color may differ, but a slot always
round-trips to the same Kanshu color.

### Deliberate limits

Kanshu does not repair highlights after the EPUB file is replaced or its XHTML changes. Existing
offsets or source paths may then become invalid; this is accepted for the current personal-reader
scope. There are no resource fingerprints, prefix or suffix selectors, fuzzy re-anchoring, conflict
history, background scheduling, cross-provider annotation sharing, or cross-spine highlights in
this design.

## Links

- [Provider Architecture](provider-architecture.md)
- [Native Reader Engine](native-reader.md)
- [Kavita API Research](../research/kavita-api.md)
- [Kavita Annotation Controller](https://github.com/Kareadita/Kavita/blob/289bc39b3e08f26cc14b8a8fd67b815ad9aaa15f/Kavita.Server/Controllers/AnnotationController.cs)
- [Kavita Annotation Injection](https://github.com/Kareadita/Kavita/blob/289bc39b3e08f26cc14b8a8fd67b815ad9aaa15f/Kavita.Services/Helpers/AnnotationHelper.cs)
