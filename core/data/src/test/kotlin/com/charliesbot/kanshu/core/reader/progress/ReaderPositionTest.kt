package com.charliesbot.kanshu.core.reader.progress

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPositionTest {

  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun roundTripSerialization() {
    val original =
      ReaderPosition(schemaVersion = 1, spineIndex = 4, pageIndex = 2, progressInSpine = 0.5f)
    val encoded = Json.encodeToString(ReaderPosition.serializer(), original)
    val decoded = Json.decodeFromString(ReaderPosition.serializer(), encoded)
    assertEquals(original, decoded)
  }

  @Test
  fun forwardCompatibilityWithUnknownFields() {
    // A serialized JSON representing a future schemaVersion with new extra fields.
    val futureJson =
      """
      {
        "schemaVersion": 2,
        "spineIndex": 5,
        "pageIndex": 3,
        "progressInSpine": 0.75,
        "newExtraField": "some new value",
        "anotherNewField": 42
      }
      """
        .trimIndent()

    val decoded = json.decodeFromString(ReaderPosition.serializer(), futureJson)
    assertEquals(2, decoded.schemaVersion)
    assertEquals(5, decoded.spineIndex)
    assertEquals(3, decoded.pageIndex)
    assertEquals(0.75f, decoded.progressInSpine)
  }

  @Test
  fun backwardCompatibilityWithDefaults() {
    // If some fields are missing (e.g. from an older version), default values should apply.
    val minimalJson =
      """
      {
        "spineIndex": 10,
        "pageIndex": 0,
        "progressInSpine": 0.0
      }
      """
        .trimIndent()

    val decoded = json.decodeFromString(ReaderPosition.serializer(), minimalJson)
    assertEquals(1, decoded.schemaVersion) // Default value
    assertEquals(10, decoded.spineIndex)
    assertEquals(0, decoded.pageIndex)
    assertEquals(0.0f, decoded.progressInSpine)
  }
}
