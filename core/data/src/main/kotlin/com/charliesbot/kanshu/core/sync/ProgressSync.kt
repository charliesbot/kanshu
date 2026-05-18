package com.charliesbot.kanshu.core.sync

import java.io.File
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

// Capability interface for providers that can sync reading progress to a remote service. v0
// has a single implementation (Kavita via kosync) but the seam exists so adding OPDS or a
// self-hosted kosync server later doesn't reshape callers.
//
// Lives in :core:data rather than :core:domain because the wire conversion is inherently
// Readium-aware — see the architectural exception in CLAUDE.md for the parallel reasoning on
// ReaderSource.
interface ProgressSync {
  // Push the device's current position to the remote. Returns failure on transport/auth errors;
  // the orchestrator decides whether to retry (typically via WorkManager).
  suspend fun push(
    file: File,
    locator: Locator,
    publication: Publication,
    timestampMillis: Long,
  ): Result<Unit>

  // Pull the remote's stored position for this book. Returns Result.success(null) when the
  // server doesn't have progress for this book yet (a normal, non-error state).
  suspend fun pull(file: File, publication: Publication): Result<RemoteProgress?>
}

data class RemoteProgress(
  val locator: Locator,
  val percentage: Double,
  val timestampMillis: Long,
  // Free-form label populated by the device that last wrote progress. Surfaced in the
  // "Continue from page X on (device)?" prompt. Null when the remote didn't include one.
  val deviceName: String?,
)
