# Provider Architecture

## Status

Proposed target architecture. This document defines the provider boundary and the migration path
from Kanshu's current Kavita-only implementation. It does not schedule the implementation.

## Goal

Kanshu should discover, acquire, open, and synchronize books without shared application code
knowing which backend supplied them. Kavita is the first provider, but the same boundary must fit
books stored on the device and future services such as BookOrbit.

A **provider** supplies readable book files. It may additionally synchronize reading progress and
highlights. Kavita, BookOrbit, and **On this device** are providers. Services such as Readwise and
Hardcover do not supply the readable file, so they are integrations rather than providers.

The boundary must preserve Kanshu's offline-first behavior:

- Room is updated before any remote synchronization.
- Every acquired EPUB is materialized in Kanshu-managed storage before it is opened.
- One unavailable provider does not prevent books from other providers, or its cached books, from
  being shown.
- Provider-specific identifiers and wire formats do not leak into reader UI or rendering code.

## Definitions

### Provider type

A compiled implementation of a backend protocol, such as `Kavita`, `Local`, or `BookOrbit`.
Runtime plugin loading is not part of this design.

### Provider instance

One configured occurrence of a provider type. Two Kavita servers are two provider instances, each
with its own stable ID, display name, credentials, configuration, and capabilities.

### Provider book

A book owned by one provider instance and identified within that instance by an opaque string. The
same EPUB supplied by Kavita, Local, and BookOrbit is represented as three books in the initial
model. Content-hash deduplication and cross-provider state sharing are deferred.

### Integration

A service that consumes or replicates reading state without supplying the book file. Integrations
may be designed later, but they do not participate in provider discovery or acquisition.

## Architectural Principles

1. **The provider owns origin, not the reading experience.** Providers discover books, supply their
   bytes, and translate optional remote state. Kanshu owns EPUB opening, parsing, rendering, local
   persistence, and sync policy.
2. **Local state is canonical for interaction.** Page turns and highlight mutations are persisted
   immediately. Remote synchronization is asynchronous replication.
3. **Capabilities are explicit.** Every provider has the same direct contract, but capability
   metadata and explicit `LocalOnly` results distinguish intentional no-ops from failures.
4. **Provider identity follows every book.** Catalog, acquisition, progress, and highlight calls are
   routed through the provider instance that owns the book.
5. **Remote metadata is opaque to shared storage.** Kavita chapter IDs, BookOrbit identifiers, and
   remote annotation IDs live in provider-owned metadata rather than shared columns.
6. **Removing a download only removes Kanshu's copy.** Deleting the provider's source book is a
   separate, future capability.

## Target Model

The public model uses opaque identifiers instead of Kavita-specific integers:

```kotlin
@JvmInline
value class ProviderInstanceId(val value: String)

@JvmInline
value class BookId(val value: String)

data class ProviderBookKey(
  val providerId: ProviderInstanceId,
  val providerItemId: String,
)

data class ProviderCapabilities(
  val progressSync: Boolean,
  val highlightSync: Boolean,
)

data class ProviderDescriptor(
  val id: ProviderInstanceId,
  val type: ProviderType,
  val displayName: String,
  val enabled: Boolean,
  val capabilities: ProviderCapabilities,
)

data class ProviderBook(
  val key: ProviderBookKey,
  val title: String,
  val cover: ProviderCover?,
  val mediaType: String,
  val revisionToken: String?,
)
```

Room assigns each imported catalog item a Kanshu `BookId` and enforces uniqueness on
`(providerInstanceId, providerItemId)`. Reading progress and annotations continue to reference the
Kanshu ID. The provider key is used only when dispatching provider operations.

Provider configuration is keyed by `ProviderInstanceId`. Non-secret configuration may live in the
database; credentials remain in provider-specific secure storage and never appear in catalog or
sync metadata.

## Provider Contract

Providers expose one direct contract matching the product concept:

```kotlin
interface Provider {
  val descriptor: ProviderDescriptor

  suspend fun fetchCatalog(): ProviderResult<List<ProviderBook>>

  suspend fun acquire(
    book: ProviderBookKey,
    target: File,
    onProgress: (downloaded: Long, total: Long?) -> Unit,
  ): ProviderResult<AcquiredBook>

  suspend fun pullProgress(context: ProviderBookContext): ProgressSyncResult

  suspend fun pushProgress(
    context: ProviderBookContext,
    position: ReaderPosition,
  ): ProgressSyncResult

  suspend fun pullHighlights(context: ProviderBookContext): HighlightSyncResult

  suspend fun pushHighlights(
    context: ProviderBookContext,
    changes: List<HighlightChange>,
  ): HighlightSyncResult
}
```

The synchronization results make no-op behavior observable:

```kotlin
sealed interface ProviderSyncResult<out T> {
  data class Success<T>(val value: T) : ProviderSyncResult<T>
  data object LocalOnly : ProviderSyncResult<Nothing>
  data class Failure(val error: ProviderError) : ProviderSyncResult<Nothing>
}
```

`LocalOnly` means the provider intentionally has no remote store. It is not reported as an error
and does not create retry work. A provider advertising a capability must return `Success` or
`Failure`; returning `LocalOnly` would be a contract violation caught by provider tests.

Provider methods are remote/provenance adapters only. They do not write Room rows, schedule
retries, choose conflict winners, or open publications.

## File Acquisition and Opening

All provider paths converge before EPUB opening:

```text
Provider catalog
      |
      v
Provider.acquire()
      |
      v
Kanshu-managed EPUB file
      |
      v
Shared EPUB opener
      |
      v
Readium Publication -> native reader
```

Remote providers download into the target managed file. The Local provider copies a selected SAF
document or a file discovered through a configured folder into the same managed storage. This
avoids separate reader paths for files, content URIs, and network streams, and ensures that a
temporary permission or network outage cannot break an already acquired book.

The current `ReaderSource` name is misleading because it opens an already downloaded file rather
than representing the full source. It is replaced by a shared EPUB opener that accepts a Kanshu
book or acquired-file record. Readium and the native reader remain provider-agnostic.

Removing a download deletes the managed file and clears its cache metadata. It never deletes the
Kavita, BookOrbit, SAF, or filesystem source.

## Unified Library

A registry owns configured provider instances:

```kotlin
interface ProviderRegistry {
  fun enabledProviders(): List<Provider>
  fun provider(id: ProviderInstanceId): Provider
}
```

The shared book repository refreshes enabled providers independently, persists their catalog
snapshots, and combines all cached rows into one observable library. Each provider has independent
refresh status:

- A successful refresh replaces that provider's catalog snapshot.
- A network or authentication failure retains its cached books and marks the provider stale.
- A failure never converts the entire unified library to an error when another provider has usable
  books.
- Disabling a provider hides it from refresh and aggregation without rewriting ownership.

Provider filters or sections may be added to the UI later, but the default product surface is one
unified library.

## Progress Synchronization

`SyncRepository` remains the local-first coordinator:

```text
ReaderPosition
      |
      +--> Room (immediate)
      |
      v
Owning Provider
      |
      +--> LocalOnly
      +--> provider wire translation and remote push
```

The repository resolves the book's provider instance, checks `progressSync`, and invokes the
provider when supported. It owns debounce behavior, retries, remote comparison, and user-facing
conflict decisions. Providers translate the canonical `ReaderPosition` into their protocols.

For Kavita, `KavitaProvider` retains the existing kosync hash and XPointer translation internally.
Those types no longer appear in the shared progress contract. Provider-specific progress metadata
is stored as opaque JSON associated with the local progress row.

## Highlight Synchronization

`AnnotationRepository` remains the local-first coordinator. Kanshu's canonical highlight is
addressed by spine index, flattened-text character offsets, selected text, color, and timestamps.
Provider implementations translate that model into their remote representation.

```text
Reader highlight mutation
      |
      +--> Room annotation + pending change (immediate)
      |
      v
Owning Provider
      |
      +--> LocalOnly
      +--> provider-specific annotation API
```

The shared repository owns the pending-change queue, retry policy, tombstones, and conflict rules.
Provider metadata stores remote IDs and the last observed remote revision without adding
Kavita-specific columns to the shared annotation schema.

Default reconciliation rules are:

- A local deletion remains as a tombstone until the provider acknowledges it.
- A remote deletion wins over a pending edit, preventing accidental resurrection.
- A change made on only one side is applied to the other side.
- If both sides changed after their last common revision, the remote version wins and the conflict
  is logged.
- A remote anchor that cannot be mapped safely is skipped rather than attached to the wrong text.

Kavita translates the canonical range to and from its XPath annotations. BookOrbit may use a
different translation while sharing the same local queue and reconciliation policy. Local reports
`LocalOnly` and retains highlights exclusively in Room.

## Responsibility Matrix

| Concern                      | Shared Kanshu | Kavita            | Local          | BookOrbit      |
| ---------------------------- | ------------- | ----------------- | -------------- | -------------- |
| Unified catalog and cache    | Yes           | API adapter       | File discovery | API adapter    |
| Managed-file lifecycle       | Yes           | Download bytes    | Copy source    | Download bytes |
| EPUB parsing and opening     | Yes           | No                | No             | No             |
| Immediate progress storage   | Yes           | No                | No             | No             |
| Remote progress translation  | No            | kosync            | `LocalOnly`    | Provider API   |
| Immediate highlight storage  | Yes           | No                | No             | No             |
| Remote highlight translation | No            | XPath annotations | `LocalOnly`    | Provider API   |
| Retry and conflict policy    | Yes           | No                | No             | No             |

BookOrbit is a valid future provider because it supplies a catalog and files and documents
bidirectional progress and annotation synchronization across its clients. Its concrete API must be
verified before implementing `BookOrbitProvider`; this design does not assume Kavita-compatible
wire formats.

## Migration Path

### 1. Establish provider identity

- Add provider instance, descriptor, key, capability, and result models.
- Persist provider ownership on books, replacing source-prefixed ID parsing as the dispatch
  mechanism.
- Add a registry containing the existing Kavita configuration as the first instance.

### 2. Adapt Kavita catalog and acquisition

- Move `BookRepositoryImpl`'s Kavita API behavior behind `KavitaProvider`.
- Make the shared repository aggregate provider catalog results and own cached download state.
- Preserve current library, download, and offline behavior before adding another provider.

### 3. Share EPUB opening

- Replace `ReaderSource.openBook(seriesId)` with a shared opener accepting the acquired Kanshu
  book.
- Keep publication construction and reader behavior unchanged.

### 4. Route progress through providers

- Move the existing `KavitaProgressSync` protocol adapter behind `KavitaProvider` methods.
- Keep `SyncRepository` responsible for Room, debounce, retry, and remote comparison.

### 5. Route highlights through providers

- Add provider-neutral pending changes, tombstones, and opaque remote metadata.
- Implement Kavita XPath conversion and annotation API calls behind `KavitaProvider`.

### 6. Add the Local provider

- Register one built-in **On this device** provider instance.
- Support individually imported documents and configured SAF folders.
- Copy acquired EPUBs into managed storage and return `LocalOnly` for both sync families.

BookOrbit is implemented only after this migration proves the boundary with Kavita and Local.

## Testing Strategy

- Provider contract tests cover catalog, acquisition, explicit `LocalOnly`, authentication,
  network failure, and malformed responses.
- Registry and repository tests cover multiple configured instances and routing by provider owner.
- Unified-library tests cover duplicate provider item IDs, partial outages, cached stale results,
  and disabled providers.
- Acquisition tests prove all providers produce managed files and cache removal never touches the
  source.
- Shared-opener regression tests prove a Kavita EPUB opens identically after `ReaderSource` is
  removed.
- Progress tests preserve existing debounce and further-remote behavior while testing Local's
  intentional no-op.
- Highlight tests cover pull, create, update, deletion tombstones, retries, conflicts, and
  unresolvable anchors.
- `./gradlew build` remains the canonical implementation gate.

## Deferred Work

- BookOrbit API implementation.
- Runtime-loaded provider plugins.
- Cross-provider content deduplication or state sharing.
- Deleting or modifying a provider's source files.
- Sync-only integrations such as Readwise and Hardcover.
- Non-EPUB acquisition and rendering.
- Provider-specific library UI beyond optional filtering.
