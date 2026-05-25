package com.charliesbot.kanshu.features.reader

import android.content.Context
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
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

class CachedResource(
  val path: String,
  val spineIndex: Int,
  val loadId: Int,
  val targetPageIndex: Int,
  val bytes: ByteArray,
  val mimeType: String,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CachedResource

    if (path != other.path) return false
    if (spineIndex != other.spineIndex) return false
    if (loadId != other.loadId) return false
    if (targetPageIndex != other.targetPageIndex) return false
    if (!bytes.contentEquals(other.bytes)) return false
    if (mimeType != other.mimeType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = path.hashCode()
    result = 31 * result + spineIndex
    result = 31 * result + loadId
    result = 31 * result + targetPageIndex
    result = 31 * result + bytes.contentHashCode()
    result = 31 * result + mimeType.hashCode()
    return result
  }
}

@OptIn(ExperimentalReadiumApi::class)
class KanshuWebViewClient(
  private val context: Context,
  private val publication: Publication,
  private val readLock: Mutex,
  private val currentChapter: CachedResource,
  private val onChapterPageFinished: (WebView, CachedResource) -> Unit = { _, _ -> },
  private val onMainFrameLoadFailed: () -> Unit = {},
) : WebViewClient() {

  private val activeChapterLoadId: Int = currentChapter.loadId

  override fun onPageFinished(view: WebView, url: String) {
    val parsed = android.net.Uri.parse(url)
    if (parsed.isCurrentChapterUrl()) {
      onChapterPageFinished(view, currentChapter)
    }
  }

  override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
    val url = request.url ?: return true
    if (url.scheme != "https" || url.host != "kanshu.invalid") return true

    val path = KanshuPathNormalizer.normalizeAndRejectTraversal(url.path.orEmpty()) ?: return true
    if (path != currentChapter.path) return true

    val loadId = url.getQueryParameter("__kanshu_load")?.toIntOrNull()
    if (loadId == activeChapterLoadId) return false

    val fragment = url.fragment ?: return true
    val target =
      url
        .buildUpon()
        .clearQuery()
        .appendQueryParameter("__kanshu_load", activeChapterLoadId.toString())
        .fragment(fragment)
        .build()
    view.loadUrl(target.toString())
    return true
  }

  override fun onReceivedError(
    view: WebView,
    request: WebResourceRequest,
    error: WebResourceError,
  ) {
    if (request.isForMainFrame && request.url.isCurrentChapterUrl()) {
      onMainFrameLoadFailed()
    }
  }

  override fun onReceivedHttpError(
    view: WebView,
    request: WebResourceRequest,
    errorResponse: WebResourceResponse,
  ) {
    if (request.isForMainFrame && request.url.isCurrentChapterUrl()) {
      onMainFrameLoadFailed()
    }
  }

  override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
    onMainFrameLoadFailed()
    return true
  }

  private fun android.net.Uri.isCurrentChapterUrl(): Boolean {
    val path = KanshuPathNormalizer.normalizeAndRejectTraversal(path.orEmpty()) ?: return false
    val loadId = getQueryParameter("__kanshu_load")?.toIntOrNull()
    return scheme == "https" &&
      host == "kanshu.invalid" &&
      path == currentChapter.path &&
      loadId == activeChapterLoadId
  }

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
          ?: getMimeTypeFromExtension(extension)
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
      val mimeType = getMimeTypeFromExtension(extension) ?: "application/octet-stream"
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

  private fun getMimeTypeFromExtension(extension: String): String? =
    when (extension.lowercase()) {
      "css" -> "text/css"
      "js" -> "application/javascript"
      "html",
      "htm" -> "text/html"
      "xhtml" -> "application/xhtml+xml"
      "png" -> "image/png"
      "jpg",
      "jpeg" -> "image/jpeg"
      "gif" -> "image/gif"
      "svg" -> "image/svg+xml"
      else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

  companion object {
    private const val TAG = "KanshuWebViewClient"
  }
}
