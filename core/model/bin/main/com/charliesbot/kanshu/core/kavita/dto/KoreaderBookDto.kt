package com.charliesbot.kanshu.core.kavita.dto

import kotlinx.serialization.Serializable

// Wire shape for /api/Koreader/{apiKey}/syncs/progress. Field names mirror what KOReader's
// kosync plugin sends and what Kavita's KoreaderController accepts. `document` is the kosync
// file hash (see KoreaderHash); `progress` is an XPointer-ish position string (see
// KoreaderPosition). `device_id` keys the row server-side so devices don't overwrite each
// other's "this device's last position" reads; we send a stable per-install id.
@Serializable
data class KoreaderBookDto(
  val document: String,
  val device_id: String,
  val device: String,
  val percentage: Float,
  val progress: String,
  val timestamp: Long,
)
