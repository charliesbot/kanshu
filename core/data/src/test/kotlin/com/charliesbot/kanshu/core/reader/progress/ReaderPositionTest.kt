package com.charliesbot.kanshu.core.reader.progress

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPositionTest {

  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun roundTripSerialization() {
    val original = ReaderPosition(spineIndex = 4, charOffset = 1_820, progressInSpine = 0.5f)
    val encoded = Json.encodeToString(ReaderPosition.serializer(), original)
    val decoded = Json.decodeFromString(ReaderPosition.serializer(), encoded)
    assertEquals(original, decoded)
  }

  @Test
  fun forwardCompatibilityWithUnknownFields() {
    // Fields a later build might add have to decode away rather than throw.
    val futureJson =
      """
      {
        "spineIndex": 5,
        "charOffset": 900,
        "progressInSpine": 0.75,
        "newExtraField": "some new value",
        "anotherNewField": 42
      }
      """
        .trimIndent()

    val decoded = json.decodeFromString(ReaderPosition.serializer(), futureJson)
    assertEquals(5, decoded.spineIndex)
    assertEquals(900, decoded.charOffset)
    assertEquals(0.75f, decoded.progressInSpine)
  }

  @Test
  fun backwardCompatibilityWithDefaults() {
    val minimalJson =
      """
      {
        "spineIndex": 10,
        "progressInSpine": 0.0
      }
      """
        .trimIndent()

    val decoded = json.decodeFromString(ReaderPosition.serializer(), minimalJson)
    assertEquals(10, decoded.spineIndex)
    assertEquals(0, decoded.charOffset)
    assertEquals(0.0f, decoded.progressInSpine)
  }

  @Test
  fun pageIndexRowsDecodeToTheChapterStart() {
    // Rows written before the native engine stored a page index and a schema version, neither of
    // which survives here. The page index cannot be mapped back to an offset without the
    // typography that produced it, so the chapter start is the honest landing spot.
    val pageIndexJson =
      """
      {
        "schemaVersion": 1,
        "spineIndex": 7,
        "pageIndex": 12,
        "progressInSpine": 0.4
      }
      """
        .trimIndent()

    val decoded = json.decodeFromString(ReaderPosition.serializer(), pageIndexJson)
    assertEquals(7, decoded.spineIndex)
    assertEquals(0, decoded.charOffset)
    assertEquals(0.4f, decoded.progressInSpine)
  }
}
