package com.charliesbot.kanshu.features.reader

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.webkit.WebResourceRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
      spineIndex = 0,
      loadId = 100,
      targetPageIndex = 0,
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

  @Test
  fun testLoadsPublicationResourceWhenDirectHrefLookupMisses() {
    val request = createMockRequest("https://kanshu.invalid/OEBPS/images/cover.jpg")

    val mockLink: Link = mockk()
    every { mockLink.href.toString() } returns "/OEBPS/images/cover.jpg"
    every { mockLink.mediaType } returns MediaType("image/jpeg")
    every { publication.linkWithHref(any()) } returns null
    every { publication.readingOrder } returns emptyList()
    every { publication.resources } returns listOf(mockLink)

    val mockResource: org.readium.r2.shared.util.resource.Resource = mockk()
    val mockImageBytes = "mock-jpeg-bytes".toByteArray()
    coEvery { mockResource.read() } returns Try.success(mockImageBytes)
    every { mockResource.close() } returns Unit
    every { publication.get(mockLink) } returns mockResource

    val response = webViewClient.shouldInterceptRequest(mockk(), request)

    assertNotNull(response)
    val res = response!!
    assertEquals(200, res.statusCode)
    assertEquals("image/jpeg", res.mimeType)
    assertTrue(res.data.readBytes().contentEquals(mockImageBytes))
  }

  @Test
  fun testAllowsSameChapterFragmentNavigationWithCurrentLoadId() {
    val request =
      createMockRequest("https://kanshu.invalid/OEBPS/chapter1.xhtml?__kanshu_load=100#footnote-1")

    val shouldOverride = webViewClient.shouldOverrideUrlLoading(mockk(), request)

    assertEquals(false, shouldOverride)
  }

  @Test
  fun testSameChapterFragmentNavigationWithoutLoadIdAppendsLoadId() {
    val request = createMockRequest("https://kanshu.invalid/OEBPS/chapter1.xhtml#footnote-1")
    val webView = mockk<android.webkit.WebView>(relaxed = true)

    val shouldOverride = webViewClient.shouldOverrideUrlLoading(webView, request)

    assertEquals(true, shouldOverride)
    verify {
      webView.loadUrl("https://kanshu.invalid/OEBPS/chapter1.xhtml?__kanshu_load=100#footnote-1")
    }
  }

  @Test
  fun testOverridesCrossChapterNavigation() {
    val request = createMockRequest("https://kanshu.invalid/OEBPS/chapter2.xhtml#section-2")

    val shouldOverride = webViewClient.shouldOverrideUrlLoading(mockk(), request)

    assertEquals(true, shouldOverride)
  }

  @Test
  fun testOverridesExternalNavigation() {
    val request = createMockRequest("https://example.com/chapter.xhtml")

    val shouldOverride = webViewClient.shouldOverrideUrlLoading(mockk(), request)

    assertEquals(true, shouldOverride)
  }

  @Test
  fun testMainFrameHttpErrorCallsFailureCallback() {
    var failed = false
    val client =
      KanshuWebViewClient(
        context = context,
        publication = publication,
        readLock = readLock,
        currentChapter = currentChapter,
        onMainFrameLoadFailed = { failed = true },
      )
    val request = createMockRequest("https://kanshu.invalid/OEBPS/chapter1.xhtml?__kanshu_load=100")
    every { request.isForMainFrame } returns true

    client.onReceivedHttpError(mockk(), request, mockk())

    assertEquals(true, failed)
  }

  @Test
  fun testMainFrameReceivedErrorCallsFailureCallback() {
    var failed = false
    val client =
      KanshuWebViewClient(
        context = context,
        publication = publication,
        readLock = readLock,
        currentChapter = currentChapter,
        onMainFrameLoadFailed = { failed = true },
      )
    val request = createMockRequest("https://kanshu.invalid/OEBPS/chapter1.xhtml?__kanshu_load=100")
    every { request.isForMainFrame } returns true

    client.onReceivedError(mockk(), request, mockk())

    assertEquals(true, failed)
  }

  @Test
  fun testStaleMainFrameHttpErrorDoesNotCallFailureCallback() {
    var failed = false
    val client =
      KanshuWebViewClient(
        context = context,
        publication = publication,
        readLock = readLock,
        currentChapter = currentChapter,
        onMainFrameLoadFailed = { failed = true },
      )
    val request = createMockRequest("https://kanshu.invalid/OEBPS/old-chapter.xhtml")
    every { request.isForMainFrame } returns true

    client.onReceivedHttpError(mockk(), request, mockk())

    assertEquals(false, failed)
  }

  @Test
  fun testStaleSameChapterLoadIdHttpErrorDoesNotCallFailureCallback() {
    var failed = false
    val client =
      KanshuWebViewClient(
        context = context,
        publication = publication,
        readLock = readLock,
        currentChapter = currentChapter,
        onMainFrameLoadFailed = { failed = true },
      )
    val request = createMockRequest("https://kanshu.invalid/OEBPS/chapter1.xhtml?__kanshu_load=99")
    every { request.isForMainFrame } returns true

    client.onReceivedHttpError(mockk(), request, mockk())

    assertEquals(false, failed)
  }

  @Test
  fun testRenderProcessGoneCallsFailureCallbackAndHandlesCrash() {
    var failed = false
    val client =
      KanshuWebViewClient(
        context = context,
        publication = publication,
        readLock = readLock,
        currentChapter = currentChapter,
        onMainFrameLoadFailed = { failed = true },
      )

    val handled = client.onRenderProcessGone(mockk(), mockk())

    assertEquals(true, handled)
    assertEquals(true, failed)
  }

  @Test
  fun testPageFinishedCallsChapterFinishedCallback() {
    var callbackChapter: CachedResource? = null
    val client =
      KanshuWebViewClient(
        context = context,
        publication = publication,
        readLock = readLock,
        currentChapter = currentChapter,
        onChapterPageFinished = { _, chapter -> callbackChapter = chapter },
      )

    client.onPageFinished(mockk(), "https://kanshu.invalid/OEBPS/chapter1.xhtml?__kanshu_load=100")

    assertEquals(currentChapter, callbackChapter)
  }

  @Test
  fun testStalePageFinishedDoesNotCallChapterFinishedCallback() {
    var callbackChapter: CachedResource? = null
    val client =
      KanshuWebViewClient(
        context = context,
        publication = publication,
        readLock = readLock,
        currentChapter = currentChapter,
        onChapterPageFinished = { _, chapter -> callbackChapter = chapter },
      )

    client.onPageFinished(mockk(), "https://kanshu.invalid/OEBPS/chapter1.xhtml?__kanshu_load=99")

    assertEquals(null, callbackChapter)
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
