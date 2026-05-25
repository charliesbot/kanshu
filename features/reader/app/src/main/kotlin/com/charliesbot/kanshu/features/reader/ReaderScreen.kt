package com.charliesbot.kanshu.features.reader

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charliesbot.kanshu.core.ui.components.KanshuButton
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import com.charliesbot.kanshu.strings.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReaderScreen(seriesId: Int, title: String, viewModel: ReaderViewModel = koinViewModel()) {
  LaunchedEffect(seriesId) { viewModel.open(seriesId) }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  ReaderContent(title = title, uiState = uiState, onNextResource = viewModel::openNextResource)
}

@Composable
private fun ReaderContent(title: String, uiState: ReaderUiState, onNextResource: () -> Unit) {
  KanshuScaffold {
    when (uiState) {
      ReaderUiState.Loading -> ReaderMessage(stringResource(R.string.reader_status_loading))
      ReaderUiState.Error.NotFound -> ReaderMessage(stringResource(R.string.reader_error_not_found))
      ReaderUiState.Error.ParseFailed ->
        ReaderMessage(stringResource(R.string.reader_error_parse_failed))
      ReaderUiState.Error.ReadFailed ->
        ReaderMessage(stringResource(R.string.reader_error_read_failed))
      is ReaderUiState.Ready ->
        ReaderWebView(title = title, state = uiState, onNextResource = onNextResource)
    }
  }
}

@Composable
private fun ReaderMessage(text: String) {
  Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    KanshuText(text = text, style = KanshuTheme.typography.bodyLarge)
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ReaderWebView(title: String, state: ReaderUiState.Ready, onNextResource: () -> Unit) {
  var webView by remember { mutableStateOf<WebView?>(null) }
  var diagnostics by remember { mutableStateOf("") }
  var overlayVisible by remember { mutableStateOf(false) }

  Box(modifier = Modifier.fillMaxSize()) {
    AndroidView(
      modifier = Modifier.fillMaxSize().padding(bottom = ReaderDiagnosticsPanelHeight),
      factory = { context ->
        WebView(context)
          .configureReaderWebView(
            onDiagnostics = { diagnostics = it },
            onTapZone = { view, tapZone ->
              when (tapZone) {
                ReaderTapZone.PreviousPage -> view.evaluateJavascript(PreviousPageScript, null)
                ReaderTapZone.Overlay -> overlayVisible = true
                ReaderTapZone.NextPage ->
                  view.evaluateJavascript(NextPageScript) { result ->
                    if (result == "true") onNextResource()
                  }
              }
            },
          )
          .also { webView = it }
      },
      update = { view ->
        if (view.tag != state.href) {
          view.tag = state.href
          view.loadDataWithBaseURL(
            "https://kanshu.invalid/reader/",
            paginatedHtml(title = title, chapterHtml = state.chapterHtml),
            "text/html",
            "UTF-8",
            null,
          )
        } else {
          view.applyNativeGeometry()
        }
      },
      onRelease = { view ->
        if (webView === view) webView = null
        view.stopLoading()
        view.removeJavascriptInterface("KanshuDiagnostics")
        view.loadUrl("about:blank")
        view.destroy()
      },
    )

    ReaderDiagnosticsPanel(
      state = state,
      diagnostics = diagnostics,
      onNextPage = {
        webView?.evaluateJavascript(NextPageScript) { result ->
          if (result == "true") onNextResource()
        }
      },
      modifier = Modifier.align(Alignment.BottomCenter),
    )

    if (overlayVisible) {
      ReaderOverlayPlaceholder(
        onDismiss = { overlayVisible = false },
        modifier = Modifier.fillMaxSize().padding(bottom = ReaderDiagnosticsPanelHeight),
      )
    }
  }
}

@Composable
private fun ReaderOverlayPlaceholder(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  val closeLabel = stringResource(R.string.reader_overlay_close)

  Box(
    modifier = modifier.clickable(onClickLabel = closeLabel, onClick = onDismiss).padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .background(KanshuTheme.colors.background)
          .border(1.dp, KanshuTheme.colors.border)
          .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      KanshuText(
        text = stringResource(R.string.reader_overlay_placeholder),
        style = KanshuTheme.typography.titleLarge,
      )
    }
  }
}

private enum class ReaderTapZone {
  PreviousPage,
  Overlay,
  NextPage,
}

private fun WebView.configureReaderWebView(
  onDiagnostics: (String) -> Unit,
  onTapZone: (WebView, ReaderTapZone) -> Unit,
): WebView = apply {
  var hasSelection = false
  val diagnosticsBridge =
    DiagnosticBridge(onDiagnostics = onDiagnostics, onSelectionChanged = { hasSelection = it })
  val tapSlop = ViewConfiguration.get(context).scaledTouchSlop
  var downX = 0f
  var downY = 0f
  var downTime = 0L

  setBackgroundColor(Color.WHITE)
  isHorizontalScrollBarEnabled = false
  isVerticalScrollBarEnabled = false
  settings.javaScriptEnabled = true
  settings.allowFileAccess = false
  settings.allowContentAccess = false
  settings.blockNetworkLoads = true
  addJavascriptInterface(diagnosticsBridge, "KanshuDiagnostics")
  setOnTouchListener { view, event ->
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downX = event.x
        downY = event.y
        downTime = event.eventTime
      }
      MotionEvent.ACTION_UP -> {
        val distanceX = kotlin.math.abs(event.x - downX)
        val distanceY = kotlin.math.abs(event.y - downY)
        val isTap =
          event.eventTime - downTime < ViewConfiguration.getLongPressTimeout() &&
            distanceX <= tapSlop &&
            distanceY <= tapSlop
        if (isTap && !hasSelection) {
          onTapZone(view as WebView, event.x.toReaderTapZone(view.width))
        }
      }
    }
    false
  }
  addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom
    ->
    if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
      (view as WebView).applyNativeGeometry()
    }
  }
  webViewClient =
    object : WebViewClient() {
      override fun onPageFinished(view: WebView, url: String?) {
        view.applyNativeGeometry()
      }
    }
}

private fun Float.toReaderTapZone(width: Int): ReaderTapZone {
  val sideWidth = width * ReaderSideTapZoneFraction
  return when {
    this < sideWidth -> ReaderTapZone.PreviousPage
    this > width - sideWidth -> ReaderTapZone.NextPage
    else -> ReaderTapZone.Overlay
  }
}

@Composable
private fun ReaderDiagnosticsPanel(
  state: ReaderUiState.Ready,
  diagnostics: String,
  onNextPage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .height(ReaderDiagnosticsPanelHeight)
        .background(ComposeColor.White)
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    KanshuButton(
      text = stringResource(R.string.reader_debug_next_page),
      onClick = onNextPage,
      modifier = Modifier.fillMaxWidth(),
    )
    if (diagnostics.isNotBlank()) {
      KanshuText(
        text = "Resource ${state.resourceIndex}/${state.resourceCount}: ${state.href}",
        style = KanshuTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
      )
      KanshuText(
        text = diagnostics,
        style = KanshuTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
      )
    }
  }
}

private val ReaderDiagnosticsPanelHeight = 280.dp

private const val ReaderSideTapZoneFraction = 0.15f

private const val NextPageScript = "window.kanshuNextPage && window.kanshuNextPage()"
private const val PreviousPageScript = "window.kanshuPreviousPage && window.kanshuPreviousPage()"

class DiagnosticBridge(
  private val onDiagnostics: (String) -> Unit,
  private val onSelectionChanged: (Boolean) -> Unit,
) {
  private val mainHandler = Handler(Looper.getMainLooper())

  @android.webkit.JavascriptInterface
  fun report(metrics: String) {
    mainHandler.post { onDiagnostics(metrics) }
  }

  @android.webkit.JavascriptInterface
  fun selectionChanged(hasSelection: Boolean) {
    mainHandler.post { onSelectionChanged(hasSelection) }
  }
}

private fun WebView.applyNativeGeometry() {
  if (width <= 0 || height <= 0) return
  val nativeViewportCssWidth = width / resources.displayMetrics.density
  val nativeViewportCssHeight = height / resources.displayMetrics.density
  evaluateJavascript(
    "window.kanshuApplyNativeGeometry && window.kanshuApplyNativeGeometry($nativeViewportCssWidth, $nativeViewportCssHeight)",
    null,
  )
}

private fun paginatedHtml(title: String, chapterHtml: String): String =
  """
  <!DOCTYPE html>
  <html>
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1" />
      <title>${title.escapeHtml()}</title>
      <style>
        html, body {
          width: 100%;
          height: 100%;
          margin: 0;
          padding: 0;
          overflow: visible !important;
          background: white;
          color: black;
        }
        #kanshu-page {
          box-sizing: border-box;
          width: var(--kanshu-native-page-width, 100vw);
          height: var(--kanshu-native-page-height, 100vh);
          padding: 32px;
          overflow: hidden;
          column-width: calc(var(--kanshu-native-page-width, 100vw) - 64px);
          column-gap: 64px;
          font-family: serif;
          font-size: 20px;
          line-height: 1.55;
        }
        #kanshu-page * {
          max-width: 100%;
          break-inside: auto;
        }
        #kanshu-page p {
          margin: 0 0 1em;
        }
      </style>
    </head>
    <body>
      <main id="kanshu-page">$chapterHtml</main>
      <script>
        (function () {
          const page = document.getElementById('kanshu-page');
          let pageStep = 0;
          let pageCount = 1;

          function numberValue(value) {
            const parsed = Number.parseFloat(value);
            return Number.isFinite(parsed) ? parsed : 0;
          }

          function measure() {
            const style = window.getComputedStyle(page);
            const columnWidth = numberValue(style.columnWidth);
            const columnGap = numberValue(style.columnGap);
            const nativeWidth = window.__kanshuNativeViewportCssWidth || null;
            const nativeHeight = window.__kanshuNativeViewportCssHeight || null;
            const pageWidth = nativeWidth || document.documentElement.clientWidth || window.innerWidth;
            pageStep = columnWidth + columnGap;
            const contentWidth = Math.max(columnWidth, page.scrollWidth - columnGap);
            pageCount = Math.max(1, Math.ceil(contentWidth / pageStep));
            const currentPage = pageStep > 0 ? Math.round(page.scrollLeft / pageStep) + 1 : 1;
            KanshuDiagnostics.report(JSON.stringify({
              container: '#kanshu-page',
              computedHeight: style.height,
              clientHeight: page.clientHeight,
              scrollWidth: page.scrollWidth,
              scrollHeight: page.scrollHeight,
              columnWidth: columnWidth,
              columnGap: columnGap,
              windowInnerWidth: window.innerWidth,
              documentElementClientWidth: document.documentElement.clientWidth,
              nativeViewportCssWidth: nativeWidth,
              nativeViewportCssHeight: nativeHeight,
              pageWidth: pageWidth,
              currentPage: currentPage,
              pageCount: pageCount,
              scrollLeft: page.scrollLeft
            }, null, 2));
          }

          function reportSelection() {
            const selection = window.getSelection();
            KanshuDiagnostics.selectionChanged(!!selection && !selection.isCollapsed && selection.toString().length > 0);
          }

          window.kanshuApplyNativeGeometry = function (nativeWidth, nativeHeight) {
            window.__kanshuNativeViewportCssWidth = nativeWidth;
            window.__kanshuNativeViewportCssHeight = nativeHeight;
            document.documentElement.style.setProperty('--kanshu-native-page-width', nativeWidth + 'px', 'important');
            document.documentElement.style.setProperty('--kanshu-native-page-height', nativeHeight + 'px', 'important');
            requestAnimationFrame(measure);
          };

          window.kanshuNextPage = function () {
            if (pageStep <= 0) measure();
            if (pageStep <= 0) return false;
            const currentPageIndex = Math.round(page.scrollLeft / pageStep);
            if (currentPageIndex >= pageCount - 1) return true;
            page.scrollLeft = Math.min(page.scrollLeft + pageStep, pageStep * (pageCount - 1));
            requestAnimationFrame(measure);
            return false;
          };

          window.kanshuPreviousPage = function () {
            if (pageStep <= 0) measure();
            if (pageStep <= 0) return false;
            page.scrollLeft = Math.max(page.scrollLeft - pageStep, 0);
            requestAnimationFrame(measure);
            return false;
          };

          document.addEventListener('selectionchange', reportSelection);
          reportSelection();
          requestAnimationFrame(measure);
        }());
      </script>
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

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun ReaderContentLoadingPreview() {
  KanshuTheme {
    ReaderContent(title = "Book", uiState = ReaderUiState.Loading, onNextResource = {})
  }
}
