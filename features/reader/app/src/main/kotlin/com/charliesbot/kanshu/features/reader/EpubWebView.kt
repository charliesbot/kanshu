package com.charliesbot.kanshu.features.reader

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val NO_HASH = Int.MIN_VALUE

// Renders one EPUB chapter as a paginated WebView. JS is required for the page-count handshake
// and `kanshuGoToPage(i)` page turning; everything else (zoom, scrollbars, overscroll) is off
// so the WebView never animates and never paints chrome that ghosts on e-ink. onRelease
// destroys the WebView so its native resources don't leak past the screen's lifecycle.
@Composable
fun EpubWebView(
  html: String,
  currentPage: Int,
  onPageCount: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val lastHash = remember { intArrayOf(NO_HASH) }
  val onPageCountState = rememberUpdatedState(onPageCount)
  val bridge = remember { KanshuJsBridge { count -> onPageCountState.value(count) } }
  AndroidView(
    factory = { context -> buildWebView(context, bridge) },
    update = { webView ->
      val nextHash = html.hashCode()
      if (lastHash[0] != nextHash) {
        lastHash[0] = nextHash
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        // currentPage is applied once the chapter's JS reports its page count; the VM then
        // updates currentPageIndex and a follow-up recomposition runs the else branch below.
      } else {
        webView.evaluateJavascript("window.kanshuGoToPage && kanshuGoToPage($currentPage);", null)
      }
    },
    onRelease = { it.destroy() },
    modifier = modifier,
  )
}

@SuppressLint("SetJavaScriptEnabled")
private fun buildWebView(context: android.content.Context, bridge: KanshuJsBridge): WebView =
  WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.builtInZoomControls = false
    settings.displayZoomControls = false
    overScrollMode = View.OVER_SCROLL_NEVER
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    setBackgroundColor(Color.WHITE)
    addJavascriptInterface(bridge, "Kanshu")
  }

// JavascriptInterface methods are invoked on a WebView worker thread; the callback hops to
// viewModelScope inside the VM, so we don't need to dispatch here.
private class KanshuJsBridge(private val onPageCount: (Int) -> Unit) {
  @JavascriptInterface
  fun onPageCount(count: Int) {
    onPageCount(count)
  }
}
