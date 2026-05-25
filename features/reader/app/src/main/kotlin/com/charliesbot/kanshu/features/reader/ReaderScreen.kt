package com.charliesbot.kanshu.features.reader

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(seriesId: Int, title: String) {
  KanshuScaffold {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { context ->
        WebView(context).apply {
          setBackgroundColor(Color.WHITE)
          settings.javaScriptEnabled = false
          loadDataWithBaseURL(
            "https://kanshu.invalid/reader-reset",
            resetHtml(title, seriesId),
            "text/html",
            "UTF-8",
            null,
          )
        }
      },
      onRelease = { webView ->
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.destroy()
      },
    )
  }
}

private fun resetHtml(title: String, seriesId: Int): String =
  """
  <!DOCTYPE html>
  <html>
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1" />
      <style>
        html, body {
          margin: 0;
          padding: 0;
          background: white;
          color: black;
          font-family: serif;
        }
        body {
          box-sizing: border-box;
          min-height: 100vh;
          padding: 32px;
        }
        h1 {
          font-size: 24px;
          font-weight: 700;
          margin: 0 0 16px;
        }
        p {
          font-size: 18px;
          line-height: 1.5;
          margin: 0 0 12px;
        }
      </style>
    </head>
    <body>
      <h1>${title.escapeHtml()}</h1>
      <p>Reader renderer reset.</p>
      <p>Series ID: $seriesId</p>
    </body>
  </html>
  """
    .trimIndent()

private fun String.escapeHtml(): String =
  replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")
