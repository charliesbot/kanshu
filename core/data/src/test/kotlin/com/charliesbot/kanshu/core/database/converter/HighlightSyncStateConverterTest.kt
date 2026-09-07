package com.charliesbot.kanshu.core.database.converter

import com.charliesbot.kanshu.core.reader.highlight.HighlightSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HighlightSyncStateConverterTest {
  private val converter = HighlightSyncStateConverter()

  @Test
  fun syncStatesUseStableStorageValues() {
    assertEquals("SYNCED", converter.toStorageValue(HighlightSyncState.SYNCED))
    assertEquals("PENDING_UPSERT", converter.toStorageValue(HighlightSyncState.PENDING_UPSERT))
    assertEquals("PENDING_DELETE", converter.toStorageValue(HighlightSyncState.PENDING_DELETE))
  }

  @Test
  fun storedValuesMapToTypedSyncStates() {
    assertEquals(HighlightSyncState.SYNCED, converter.fromStorageValue("SYNCED"))
    assertEquals(
      HighlightSyncState.PENDING_UPSERT,
      converter.fromStorageValue("PENDING_UPSERT"),
    )
    assertEquals(
      HighlightSyncState.PENDING_DELETE,
      converter.fromStorageValue("PENDING_DELETE"),
    )
  }

  @Test
  fun unknownStoredValuesFailInsteadOfBecomingSynced() {
    assertThrows(IllegalArgumentException::class.java) {
      converter.fromStorageValue("UNKNOWN_STATE")
    }
  }
}
