package com.charliesbot.kanshu.core.reader.annotation

import com.charliesbot.kanshu.core.database.dao.AnnotationDao
import com.charliesbot.kanshu.core.database.entity.AnnotationEntity
import io.mockk.coEvery
import io.mockk.coVerify
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
          createdAt = 5L,
        )
      ),
      annotations,
    )
  }

  private fun repository(dao: AnnotationDao): AnnotationRepository =
    AnnotationRepositoryImpl(annotationDao = dao, now = { 1_700L }, newId = { "annotation-id" })
}
