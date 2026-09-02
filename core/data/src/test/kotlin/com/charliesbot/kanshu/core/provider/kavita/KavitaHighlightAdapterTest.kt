package com.charliesbot.kanshu.core.provider.kavita

import com.charliesbot.kanshu.core.connection.CredentialsRepository
import com.charliesbot.kanshu.core.connection.KavitaCredentials
import com.charliesbot.kanshu.core.kavita.KavitaApi
import com.charliesbot.kanshu.core.kavita.dto.AnnotationDto
import com.charliesbot.kanshu.core.provider.HighlightChange
import com.charliesbot.kanshu.core.provider.ProviderBookContext
import com.charliesbot.kanshu.core.provider.ProviderBookKey
import com.charliesbot.kanshu.core.provider.ProviderHighlightContext
import com.charliesbot.kanshu.core.provider.ProviderHighlightSnapshot
import com.charliesbot.kanshu.core.provider.ProviderResult
import com.charliesbot.kanshu.core.provider.ProviderSourceElement
import com.charliesbot.kanshu.core.provider.ProviderSourceMap
import com.charliesbot.kanshu.core.reader.ReaderHighlightColor
import com.charliesbot.kanshu.core.reader.SourceElementPath
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.publication.Publication

class KavitaHighlightAdapterTest {
  private val api = mockk<KavitaApi>()
  private val credentials =
    mockk<CredentialsRepository> {
      coEvery { this@mockk.credentials } returns
        flowOf(KavitaCredentials("https://kavita.example", "key"))
    }
  private val provider = KavitaProvider(credentials, api)
  private val sourceMap = FakeSourceMap()

  @Test
  fun xpathTranslationUsesLowercaseOneBasedSameTagIndexes() {
    assertEquals(
      "//body/div[1]/p[2]",
      toKavitaXPath(SourceElementPath(listOf(0, 1)), sourceMap),
    )
    assertEquals(
      SourceElementPath(listOf(0, 1)),
      resolveKavitaXPath("/html/body/div[1]/p[2]", sourceMap),
    )
    assertEquals(
      SourceElementPath(listOf(0, 1)),
      resolveKavitaXPath("id(\"target\")", sourceMap),
    )
  }

  @Test
  fun colorSlotsRoundTripWithoutUsingEnumOrdinals() {
    ReaderHighlightColor.entries.forEach { color ->
      assertEquals(color, colorForSlot(slotForColor(color)))
    }
    assertEquals(0, slotForColor(ReaderHighlightColor.Aqua))
    assertEquals(1, slotForColor(ReaderHighlightColor.Green))
    assertEquals(2, slotForColor(ReaderHighlightColor.Yellow))
    assertEquals(3, slotForColor(ReaderHighlightColor.Orange))
    assertEquals(4, slotForColor(ReaderHighlightColor.Pink))
  }

  @Test
  fun createSendsKavitaPayloadAndReturnsRemoteId() = runTest {
    val payload = slot<AnnotationDto>()
    coEvery { api.createAnnotation(any(), any(), capture(payload)) } answers
      {
        payload.captured.copy(id = 91)
      }
    val change =
      HighlightChange.Upsert(
        localId = "local",
        remoteId = null,
        expectedUpdatedAt = 2_000L,
        spineIndex = 3,
        startCharOffset = 10,
        endCharOffset = 15,
        selectedText = "words",
        startElementPath = SourceElementPath(listOf(0, 0)),
        endElementPath = SourceElementPath(listOf(0, 1)),
        color = ReaderHighlightColor.Green,
        createdAt = 1_000L,
      )

    val result = provider.pushHighlight(context(), change) as ProviderResult.Success

    assertEquals("91", result.value.remoteId)
    assertEquals("//body/div[1]/p[1]", payload.captured.xPath)
    assertEquals("//body/div[1]/p[2]", payload.captured.endingXPath)
    assertEquals(1, payload.captured.selectedSlotIndex)
    assertEquals(3, payload.captured.pageNumber)
    assertEquals(30, payload.captured.chapterId)
    assertEquals(20, payload.captured.volumeId)
    assertEquals(10, payload.captured.seriesId)
    assertEquals(5, payload.captured.libraryId)
  }

  @Test
  fun pullRetainsMalformedRemoteIdsInSeenSet() = runTest {
    coEvery { api.listAnnotations(any(), any(), 10) } returns
      listOf(
        annotation(id = 1, xPath = "//body/div[1]/p[1]", text = "words"),
        annotation(id = 2, xPath = "//body/missing[1]", text = "words"),
      )

    val result = provider.pullHighlights(context()) as ProviderResult.Success
    val snapshot = result.value as ProviderHighlightSnapshot

    assertEquals(setOf("1", "2"), snapshot.seenRemoteIds)
    assertEquals(listOf("1"), snapshot.highlights.map { it.remoteId })
    assertEquals(10, snapshot.highlights.single().startCharOffset)
    assertEquals(15, snapshot.highlights.single().endCharOffset)
  }

  @Test
  fun failedMutationReturnsProviderFailure() = runTest {
    coEvery { api.deleteAnnotation(any(), any(), 7) } throws
      com.charliesbot.kanshu.core.kavita.KavitaException.NetworkError

    val result =
      provider.pushHighlight(
        context(),
        HighlightChange.Delete("local", "7", expectedUpdatedAt = 4L),
      )

    assertTrue(result is ProviderResult.Failure)
    coVerify { api.deleteAnnotation("https://kavita.example", "key", 7) }
  }

  private fun context() =
    ProviderHighlightContext(
      book =
        ProviderBookContext(
          book = ProviderBookKey(KavitaProvider.ID, "10"),
          file = File("book.epub"),
          publication = mockk<Publication>(),
          providerMetadata =
            mapOf(
              KavitaProvider.SERIES_ID to "10",
              KavitaProvider.LIBRARY_ID to "5",
              KavitaProvider.VOLUME_ID to "20",
              KavitaProvider.CHAPTER_ID to "30",
            ),
        ),
      sourceMapForSpine = { sourceMap },
    )

  private fun annotation(id: Int, xPath: String, text: String) =
    AnnotationDto(
      id = id,
      xPath = xPath,
      endingXPath = xPath,
      selectedText = text,
      selectedSlotIndex = 2,
      pageNumber = 0,
      chapterId = 30,
      volumeId = 20,
      seriesId = 10,
      libraryId = 5,
      createdUtc = "2026-01-01T00:00:00Z",
    )
}

private class FakeSourceMap : ProviderSourceMap {
  private val root = SourceElementPath.Root
  private val div = SourceElementPath(listOf(0))
  private val first = SourceElementPath(listOf(0, 0))
  private val second = SourceElementPath(listOf(0, 1))
  private val elements =
    listOf(
        ProviderSourceElement(root, "body", null, 0, listOf(div), 0..20),
        ProviderSourceElement(div, "DIV", null, 0, listOf(first, second), 0..20),
        ProviderSourceElement(first, "P", null, 0, emptyList(), 10..14),
        ProviderSourceElement(second, "P", "target", 1, emptyList(), 15..20),
      )
      .associateBy { it.path }

  override fun inspect(path: SourceElementPath): ProviderSourceElement? = elements[path]

  override fun resolveChild(
    parent: SourceElementPath,
    elementChildIndex: Int,
  ): SourceElementPath? = elements[parent]?.childPaths?.getOrNull(elementChildIndex)

  override fun resolveElementId(id: String): SourceElementPath? =
    elements.values.firstOrNull { it.id == id }?.path

  override fun findFirstLiteralMatch(
    startElementPath: SourceElementPath,
    endElementPath: SourceElementPath,
    selectedText: String,
  ): IntRange? = if (selectedText == "words") 10 until 15 else null
}
