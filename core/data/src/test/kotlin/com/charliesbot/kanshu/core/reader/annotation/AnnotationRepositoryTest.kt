package com.charliesbot.kanshu.core.reader.annotation

import com.charliesbot.kanshu.core.database.dao.AnnotationDao
import com.charliesbot.kanshu.core.database.entity.AnnotationEntity
import com.charliesbot.kanshu.core.reader.ReaderHighlightColor
import com.charliesbot.kanshu.core.reader.SourceElementPath
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnotationRepositoryTest {
  @Test
  fun `updateHighlightColor updates the stored annotation color and timestamp`() = runTest {
    val dao =
      mockk<AnnotationDao> {
        coEvery { find("annotation-id") } returns annotationEntity("annotation-id", "YELLOW")
        coEvery { updateColor("annotation-id", "AQUA", 1_700L, "SYNCED") } returns Unit
      }

    repository(dao).updateHighlightColor("annotation-id", ReaderHighlightColor.Aqua)

    coVerify(exactly = 1) {
      dao.updateColor("annotation-id", "AQUA", 1_700L, "SYNCED")
    }
  }

  @Test
  fun `addHighlight stores the offsets and returns the annotation`() = runTest {
    val stored = slot<AnnotationEntity>()
    val dao = mockk<AnnotationDao> { coEvery { upsert(capture(stored)) } returns Unit }

    val annotation =
      repository(dao)
        .addHighlight(
          bookId = "kavita:7",
          spineIndex = 3,
          startCharOffset = 100,
          endCharOffset = 140,
          selectedText = "a highlighted phrase",
        )

    assertEquals("annotation-id", annotation?.id)
    assertEquals("kavita:7", stored.captured.bookId)
    assertEquals(3, stored.captured.spineIndex)
    assertEquals(100, stored.captured.startCharOffset)
    assertEquals(140, stored.captured.endCharOffset)
    assertEquals("a highlighted phrase", stored.captured.selectedText)
    assertEquals(1_700L, stored.captured.createdAt)
  }

  @Test
  fun `an empty or inverted range is rejected without touching the dao`() = runTest {
    val dao = mockk<AnnotationDao>()

    assertNull(repository(dao).addHighlight("kavita:7", 0, 10, 10, ""))
    assertNull(repository(dao).addHighlight("kavita:7", 0, 10, 4, "backwards"))

    coVerify(exactly = 0) { dao.upsert(any()) }
  }

  @Test
  fun `observeForSpine maps rows to annotations`() = runTest {
    val dao =
      mockk<AnnotationDao> {
        coEvery { observeForSpine("kavita:7", 3) } returns
          flowOf(
            listOf(
              AnnotationEntity(
                id = "a",
                bookId = "kavita:7",
                spineIndex = 3,
                startCharOffset = 10,
                endCharOffset = 20,
                selectedText = "words",
                createdAt = 5L,
                updatedAt = 5L,
              )
            )
          )
      }

    val annotations = repository(dao).observeForSpine("kavita:7", 3).first()

    assertEquals(
      listOf(
        ReaderAnnotation(
          id = "a",
          spineIndex = 3,
          startCharOffset = 10,
          endCharOffset = 20,
          selectedText = "words",
          bookId = "kavita:7",
          createdAt = 5L,
        )
      ),
      annotations,
    )
  }

  @Test
  fun syncCapableCreateStoresPathsAsPendingUpsert() = runTest {
    val stored = slot<AnnotationEntity>()
    val dao = mockk<AnnotationDao> { coEvery { upsert(capture(stored)) } returns Unit }

    repository(dao, syncEnabled = true)
      .addHighlight(
        bookId = "kavita:7",
        spineIndex = 2,
        startCharOffset = 4,
        endCharOffset = 9,
        selectedText = "words",
        startElementPath = SourceElementPath(listOf(0, 1)),
        endElementPath = SourceElementPath(listOf(0, 2)),
      )

    assertEquals("PENDING_UPSERT", stored.captured.syncState)
    assertEquals("[0,1]", stored.captured.startElementPath)
    assertEquals("[0,2]", stored.captured.endElementPath)
  }

  @Test
  fun syncCapableLinkedDeleteLeavesPendingTombstone() = runTest {
    val row = annotationEntity("annotation-id", "YELLOW").copy(remoteId = "remote-1")
    val dao =
      mockk<AnnotationDao> {
        coEvery { find("annotation-id") } returns row
        coEvery { markPendingDelete("annotation-id", 1_700L) } returns Unit
      }

    repository(dao, syncEnabled = true).delete("annotation-id")

    coVerify { dao.markPendingDelete("annotation-id", 1_700L) }
    coVerify(exactly = 0) { dao.delete(any()) }
  }

  @Test
  fun `stored colors accept legacy casing and unknown values fall back to yellow`() = runTest {
    val rows =
      listOf(
        annotationEntity(id = "legacy", color = "aqua"),
        annotationEntity(id = "unknown", color = "not-a-color"),
      )
    val dao = mockk<AnnotationDao> { every { observeForSpine("kavita:7", 3) } returns flowOf(rows) }

    val annotations = repository(dao).observeForSpine("kavita:7", 3).first()

    assertEquals(
      listOf(ReaderHighlightColor.Aqua, ReaderHighlightColor.Yellow),
      annotations.map { it.color },
    )
  }

  private fun repository(
    dao: AnnotationDao,
    syncEnabled: Boolean = false,
  ): AnnotationRepository =
    AnnotationRepositoryImpl(
      annotationDao = dao,
      highlightSyncEnabled = { syncEnabled },
      now = { 1_700L },
      newId = { "annotation-id" },
    )
}

private fun annotationEntity(id: String, color: String): AnnotationEntity =
  AnnotationEntity(
    id = id,
    bookId = "kavita:7",
    spineIndex = 3,
    startCharOffset = 10,
    endCharOffset = 20,
    selectedText = "words",
    color = color,
    createdAt = 5L,
    updatedAt = 5L,
  )
