# Provider Architecture

**Date:** 2026-08-09

## TL;DR

Kanshu treats every book origin as a provider instance with a common catalog, acquisition,
progress, and highlight contract. Providers own remote protocols and provenance; shared Kanshu
repositories own managed files, local-first persistence, retries, and conflict policy. Every
provider converges on the same managed EPUB and native-reader path, so backend details never leak
into the library or reader UI.

## Design

### Provider boundary

A provider supplies readable book files and may also replicate progress or highlights. Kavita, a
future local-files implementation, and a future BookOrbit implementation fit this definition. A
service that only consumes reading state, such as Readwise or Hardcover, is an integration rather
than a provider and does not participate in discovery or acquisition.

Provider types are compiled implementations, not runtime plugins. A provider instance is one
configured occurrence of a type: two Kavita servers are distinct instances with independent IDs,
credentials, capabilities, and catalog state.

The same EPUB exposed by multiple providers remains multiple books. Content-hash deduplication and
cross-provider state sharing are intentionally outside this model.

### Ownership principles

- Providers discover books, supply bytes, and translate optional remote state. Kanshu owns EPUB
  opening, parsing, rendering, managed storage, persistence, and synchronization policy.
- Local state is canonical for interaction. Progress and highlight mutations are persisted before
  remote replication.
- Provider identity follows every book through catalog, acquisition, progress, and highlight
  routing.
- Provider wire identifiers and metadata remain opaque to shared storage and UI code.
- Optional remote operations have successful empty or no-op defaults; capabilities describe the
  provider rather than changing its interface.
- Removing a download deletes only Kanshu's managed copy. Mutating a provider's source is a
  separate capability and is not implied by cache removal.

### Identity and model

`ProviderInstanceId` identifies configuration and routing. `ProviderBookKey` combines that instance
with the provider's opaque item ID. Room assigns each imported catalog item a Kanshu `BookId` and
enforces uniqueness on `(providerInstanceId, providerItemId)`.

```kotlin
data class ProviderBookKey(
  val providerId: ProviderInstanceId,
  val providerItemId: String,
)

data class ProviderDescriptor(
  val id: ProviderInstanceId,
  val type: ProviderType,
  val displayName: String,
  val enabled: Boolean,
  val capabilities: ProviderCapabilities,
)
```

Reading progress and annotations reference the Kanshu `BookId`. The provider key appears only when
dispatching provider operations. Non-secret configuration may live in shared persistence;
credentials remain in provider-specific secure storage and never enter catalog or sync metadata.

### Provider contract

Every provider implements the same direct contract:

```kotlin
interface Provider {
  val descriptor: ProviderDescriptor

  suspend fun fetchCatalog(): ProviderResult<List<ProviderBook>>
  suspend fun resolveCover(
    book: ProviderBookKey,
    revisionToken: String?,
  ): ProviderCover?
  suspend fun acquire(
    book: ProviderBookKey,
    target: File,
    onProgress: (downloaded: Long, total: Long?) -> Unit,
  ): ProviderResult<AcquiredBook>

  suspend fun pullProgress(
    context: ProviderBookContext,
  ): ProviderResult<RemoteProgress?>
  suspend fun pushProgress(
    context: ProviderBookContext,
    position: ReaderPosition,
  ): ProviderResult<Unit>

  suspend fun pullHighlights(
    context: ProviderBookContext,
  ): ProviderResult<List<ProviderHighlight>>
  suspend fun pushHighlights(
    context: ProviderBookContext,
    changes: List<HighlightChange>,
  ): ProviderResult<Unit>
}
```

`ProviderResult` is the common fallible result for remote operations. Default progress and
highlight methods return successful empty or no-op values. Coordinators always call the owning
provider; they do not branch on capabilities or select feature-specific interfaces.

Provider methods are protocol adapters. They do not write Room rows, schedule retries, choose
conflict winners, aggregate catalogs, or open publications.

### Acquisition and opening

All providers converge on a Kanshu-managed file:

```text
Provider catalog
  -> Provider.acquire()
  -> Kanshu-managed EPUB
  -> shared EPUB opener
  -> Readium Publication
  -> native reader
```

Remote providers download into the supplied target. A local-files provider copies a selected SAF
document or discovered file into the same storage rather than maintaining a separate content-URI
reader path. Once acquired, temporary network or URI permission failures cannot break the cached
book.

The shared opener accepts the acquired Kanshu book, constructs the Readium publication, and remains
provider-agnostic. Cache removal deletes the managed file and clears local cache metadata without
touching Kavita, SAF, filesystem, or future-provider sources.

### Unified library

`ProviderRegistry` resolves configured instances by `ProviderInstanceId` and enumerates enabled
providers. The shared book repository refreshes providers independently, persists per-provider
catalog snapshots, and combines cached rows into one observable library.

A successful refresh replaces only that provider's snapshot. Authentication or network failure
retains its cached books and marks that provider stale. One unavailable provider cannot turn the
whole library into an error while another provider or cached snapshot remains usable. Disabling a
provider removes it from refresh and aggregation without rewriting book ownership.

The default UI is one library. Provider filters or sections may be layered over the shared model
without creating provider-specific library screens.

### Progress replication

`ProgressRepository` is the local-first coordinator. It writes `ReaderPosition` to Room
immediately, resolves the book's provider, and then delegates remote pull or push. The repository
owns debounce, retries, remote comparison, and conflict decisions; the provider translates the
canonical position into its wire protocol.

Kavita-specific kosync hashes and XPointer conversion stay inside `KavitaProvider`. A remote record
whose anchor cannot be decoded may still expose comparable percentage and timestamp metadata; it
must not silently become a false precise local position.

### Highlight replication

The canonical highlight uses spine index and flattened-text character offsets. Shared annotation
persistence owns immediate local writes, pending changes, tombstones, and retries. Providers
translate between that canonical range and their remote representation; a provider without remote
annotations uses the successful no-op implementation and keeps highlights exclusively in Room.

The complete local model, synchronization order, conflict behavior, source-element translation,
and Kavita XPath handling are defined in
[Highlight Persistence and Synchronization](highlight-persistence-and-sync.md).

### Capability and failure semantics

Capabilities are descriptive metadata for UI and diagnostics, not gates around delegation. A
provider that does not synchronize progress or highlights still participates through successful
defaults. This keeps orchestration uniform and prevents capability combinations from fragmenting
the contract.

Catalog, cover, acquisition, progress, and highlight failures remain scoped to the owning provider
and operation. Shared repositories decide what cached data remains visible and whether an action
is retried. Provider errors never carry wire DTOs beyond the adapter boundary.

### Extension boundaries

A local-files provider fits by discovering SAF-backed files, copying them into managed storage,
and inheriting no-op remote synchronization. BookOrbit fits only after its actual API is verified;
the shared contract does not assume Kavita-compatible identifiers or wire formats.

Runtime-loaded plugins, source-file deletion, cross-provider deduplication, non-EPUB rendering,
and sync-only services are separate designs. They must not expand the provider contract until the
product needs them.

## Links

- [Kanshu PRD](../PRD.md)
- [Native Reader Engine](native-reader.md)
- [Highlight Persistence and Synchronization](highlight-persistence-and-sync.md)
- [Kavita API research](../research/kavita-api.md)
