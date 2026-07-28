package com.charliesbot.kanshu.core.sync

import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import java.io.File
import org.readium.r2.shared.publication.Publication

/**
 * Capability interface for providers that can sync reading progress to a remote service.
 *
 * v0 has a single implementation (Kavita via kosync), but the seam exists so adding OPDS or a
 * self-hosted kosync server later doesn't reshape callers.
 *
 * Lives in `:core:data` rather than `:core:domain` because the wire conversion is inherently
 * Readium-aware — see the architectural exception in CLAUDE.md for the parallel reasoning on
 * `ReaderSource`.
 */
interface ProgressSync {
  /**
   * Push the device's current position to the remote. Returns failure on transport or auth errors;
   * the orchestrator decides whether to retry, typically via WorkManager.
   */
  suspend fun push(
    file: File,
    position: ReaderPosition,
    publication: Publication,
    timestampMillis: Long,
  ): Result<Unit>

  /**
   * Pull the remote's stored position for this book. Returns `Result.success(null)` when the server
   * doesn't have progress for this book yet, which is a normal, non-error state.
   */
  suspend fun pull(file: File, publication: Publication): Result<RemoteProgress?>
}

/**
 * @property position Null when the remote's position couldn't be decoded into our spine model —
 *   another kosync client's XPointer, or the numeric-only form Kavita sends for PDFs. The record is
 *   still reported rather than dropped, because [percentage] alone is enough to tell that the
 *   remote is further along, and dropping it would silently disarm the pre-push check.
 * @property deviceName Free-form label populated by the device that last wrote progress. Surfaced
 *   in the "Continue from page X on (device)?" prompt. Null when the remote didn't include one.
 */
data class RemoteProgress(
  val position: ReaderPosition?,
  val percentage: Double,
  val timestampMillis: Long,
  val deviceName: String?,
)
