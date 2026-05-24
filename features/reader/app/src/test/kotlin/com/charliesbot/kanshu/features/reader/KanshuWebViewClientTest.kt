package com.charliesbot.kanshu.features.reader

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.webkit.WebResourceRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import kotlinx.coroutines.sync.Mutex
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalReadiumApi::class)
class KanshuWebViewClientTest {

  private val context: Context = mockk(relaxed = true)
  private val assetManager: AssetManager = mockk(relaxed = true)
  private val publication: Publication = mockk()
  private val readLock = Mutex()

  private val mockChapterBytes = "<html><body>Chapter Contents</body></html>".toByteArray()
  private val currentChapter =
    CachedResource(
      path = "OEBPS/chapter1.xhtml",
      loadId = 100,
      bytes = mockChapterBytes,
      mimeType = "application/xhtml+xml",
    )

  private lateinit var webViewClient: KanshuWebViewClient

  @Before
  fun setUp() {
    every { context.assets } returns assetManager
    webViewClient = KanshuWebViewClient(context, publication, readLock, currentChapter)
  }

  private fun createMockRequest(urlStr: String, method: String = "GET"): WebResourceRequest {
    val request = mockk<WebResourceRequest>()
    every { request.url } returns Uri.parse(urlStr)
    every { request.method } returns method
    return request
  }

  @Test
  fun testRejectsNonInvalidDomains() {
    val request = createMockRequest("https://google.com/search")

    val response = webViewClient.shouldInterceptRequest(mockk(), request)
    assertNotNull(response)
    assertEquals(403, response!!.statusCode)
  }

  @Test
  fun testRejectsNonGetRequests() {
    val request = createMockRequest("https://kanshu.invalid/OEBPS/chapter1.xhtml", method = "POST")

    val response = webViewClient.shouldInterceptRequest(mockk(), request)
    assertNotNull(response)
    assertEquals(403, response!!.statusCode)
  }

  @Test
  fun testInterceptsPreloadedChapterWithLoadId() {
    val request = createMockRequest("https://kanshu.invalid/OEBPS/chapter1.xhtml?__kanshu_load=100")

    val response = webViewClient.shouldInterceptRequest(mockk(), request)
    assertNotNull(response)
    val res = response!!
    assertEquals(200, res.statusCode)
    assertEquals("application/xhtml+xml", res.mimeType)
    assertEquals("*", res.responseHeaders["Access-Control-Allow-Origin"])
    assertEquals("no-store", res.responseHeaders["Cache-Control"])

    val streamBytes = res.data.readBytes()
    assertTrue(streamBytes.contentEquals(mockChapterBytes))
  }

  @Test
  fun testRejectsPreloadedChapterWithMismatchedLoadId() {
    val request = createMockRequest("https://kanshu.invalid/OEBPS/chapter1.xhtml?__kanshu_load=200")

    val response = webViewClient.shouldInterceptRequest(mockk(), request)
    assertNotNull(response)
    assertEquals(404, response!!.statusCode)
  }

  @Test
  fun testRoutesReservedLocalAssets() {
    val request = createMockRequest("https://kanshu.invalid/__kanshu__/kanshu-reader.css")

    val mockCssBytes = "body { margin: 0; }".toByteArray()
    every { assetManager.open("kanshu-reader.css") } returns ByteArrayInputStream(mockCssBytes)

    val response = webViewClient.shouldInterceptRequest(mockk(), request)
    assertNotNull(response)
    val res = response!!
    assertEquals(200, res.statusCode)
    assertEquals("text/css", res.mimeType)

    val streamBytes = res.data.readBytes()
    assertTrue(streamBytes.contentEquals(mockCssBytes))
  }

  @Test
  fun testForbidsDirectSpineDocumentNavigation() {
    val request = createMockRequest("https://kanshu.invalid/OEBPS/chapter2.xhtml")

    val mockLink: Link = mockk()
    every { mockLink.mediaType } returns MediaType("application/xhtml+xml")
    every { publication.linkWithHref(any()) } returns mockLink

    val response = webViewClient.shouldInterceptRequest(mockk(), request)
    assertNotNull(response)
    assertEquals(403, response!!.statusCode)
  }

  @Test
  fun testLoadsPublicationSubResourceImage() {
    val request = createMockRequest("https://kanshu.invalid/OEBPS/images/fig1.png")

    val mockLink: Link = mockk()
    every { mockLink.mediaType } returns MediaType("image/png")
    every { publication.linkWithHref(any()) } returns mockLink

    val mockResource: org.readium.r2.shared.util.resource.Resource = mockk()
    val mockImageBytes = "mock-png-bytes".toByteArray()
    coEvery { mockResource.read() } returns Try.success(mockImageBytes)
    every { mockResource.close() } returns Unit
    every { publication.get(any<Link>()) } returns mockResource

    val response = webViewClient.shouldInterceptRequest(mockk(), request)
    assertNotNull(response)
    val res = response!!
    assertEquals(200, res.statusCode)
    assertEquals("image/png", res.mimeType)

    val streamBytes = res.data.readBytes()
    assertTrue(streamBytes.contentEquals(mockImageBytes))
  }

  private fun assertNotNull(actual: Any?) {
    org.junit.Assert.assertNotNull(actual)
  }

  private fun assertEquals(expected: Any?, actual: Any?) {
    org.junit.Assert.assertEquals(expected, actual)
  }

  private fun assertTrue(value: Boolean) {
    org.junit.Assert.assertTrue(value)
  }
}
