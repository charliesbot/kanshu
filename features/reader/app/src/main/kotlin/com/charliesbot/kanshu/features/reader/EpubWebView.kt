package com.charliesbot.kanshu.features.reader

import android.graphics.Color
import android.view.View
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

// Renders a single processed chapter HTML in a WebView. JS, zoom controls, overscroll, and
// scrollbars are all off so the WebView never animates and never paints chrome — both ghost on
// e-ink. The composition-scoped `lastHash` skips redundant loadDataWithBaseURL calls on
// recompositions where the html hasn't changed; onRelease destroys the WebView so its native
// resources don't leak past the screen's lifecycle.
@Composable
fun EpubWebView(html: String, modifier: Modifier = Modifier) {
  val lastHash = remember { intArrayOf(NO_HASH) }
  AndroidView(
    factory = { context ->
      WebView(context).apply {
        settings.javaScriptEnabled = false
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(Color.WHITE)
      }
    },
    update = { webView ->
      val nextHash = html.hashCode()
      if (lastHash[0] != nextHash) {
        lastHash[0] = nextHash
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
      }
    },
    onRelease = { it.destroy() },
    modifier = modifier,
  )
}

private const val NO_HASH = Int.MIN_VALUE
