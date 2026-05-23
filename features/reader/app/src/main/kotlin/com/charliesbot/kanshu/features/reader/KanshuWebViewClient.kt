package com.charliesbot.kanshu.features.reader

import android.content.Context
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

data class CachedResource(
  val path: String,
  val loadId: Int,
  val bytes: ByteArray,
  val mimeType: String,
)

@OptIn(ExperimentalReadiumApi::class)
class KanshuWebViewClient(
  private val context: Context,
  private val publication: Publication,
  private val readLock: Mutex,
  private val currentChapter: CachedResource,
) : WebViewClient() {

  @Volatile private var activeChapterLoadId: Int = currentChapter.loadId

  override fun shouldInterceptRequest(
    view: WebView,
    request: WebResourceRequest,
  ): WebResourceResponse? {
    val url = request.url ?: return null
    if (url.scheme != "https" || url.host != "kanshu.invalid") return forbidden()
    if (request.method != "GET") return forbidden()

    val path =
      KanshuPathNormalizer.normalizeAndRejectTraversal(url.path.orEmpty()) ?: return forbidden()

    // Route local assets
    if (path.startsWith("__kanshu__/")) {
      val assetPath = path.removePrefix("__kanshu__/")
      return assetResponse(context, assetPath)
    }

    // Route active preloaded chapter document
    val isChapterDoc = path == currentChapter.path
    if (isChapterDoc) {
      val loadId = url.getQueryParameter("__kanshu_load")?.toIntOrNull()
      if (loadId != activeChapterLoadId) {
        Log.w(TAG, "Load ID mismatch: expected $activeChapterLoadId, got $loadId")
        return notFound()
      }
      return ok(currentChapter.mimeType, currentChapter.bytes)
    }

    // Resolve publication resource
    val href = Url.fromDecodedPath(path) ?: return notFound()
    val link = publication.linkWithHref(href) ?: return notFound()

    // Prevent direct navigation to other spine chapters within WebView client; route through Kotlin
    // instead
    val isSpineChapterDoc =
      link.mediaType?.let { it.matches("application/xhtml+xml") || it.matches("text/html") } == true
    if (isSpineChapterDoc) {
      return forbidden()
    }

    val resource = publication.get(link) ?: return notFound()
    return try {
      val bytes =
        runBlocking(Dispatchers.IO) { readLock.withLock { resource.read().getOrNull() } }
          ?: return notFound()

      val extension = path.substringAfterLast('.', missingDelimiterValue = "")
      val mimeType =
        link.mediaType?.toString()
          ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
          ?: "application/octet-stream"

      ok(mimeType, bytes)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load resource: $path", e)
      notFound()
    } finally {
      resource.close()
    }
  }

  private fun forbidden(): WebResourceResponse =
    WebResourceResponse(
      "text/plain",
      "UTF-8",
      403,
      "Forbidden",
      localHeaders(),
      ByteArrayInputStream(ByteArray(0)),
    )

  private fun notFound(): WebResourceResponse =
    WebResourceResponse(
      "text/plain",
      "UTF-8",
      404,
      "Not Found",
      localHeaders(),
      ByteArrayInputStream(ByteArray(0)),
    )

  private fun ok(mimeType: String, bytes: ByteArray): WebResourceResponse =
    WebResourceResponse(
      mimeType,
      responseEncoding(mimeType),
      200,
      "OK",
      localHeaders(),
      ByteArrayInputStream(bytes),
    )

  private fun assetResponse(context: Context, path: String): WebResourceResponse {
    return try {
      val inputStream = context.assets.open(path)
      val extension = path.substringAfterLast('.', missingDelimiterValue = "")
      val mimeType =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
      WebResourceResponse(
        mimeType,
        responseEncoding(mimeType),
        200,
        "OK",
        localHeaders(),
        inputStream,
      )
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load asset: $path", e)
      notFound()
    }
  }

  private fun responseEncoding(mimeType: String): String? =
    when {
      mimeType.startsWith("text/") -> "UTF-8"
      mimeType.contains("xml") -> "UTF-8"
      mimeType == "application/javascript" -> "UTF-8"
      else -> null
    }

  private fun localHeaders(): Map<String, String> =
    mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store")

  companion object {
    private const val TAG = "KanshuWebViewClient"
  }
}
