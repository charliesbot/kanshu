package com.charliesbot.kanshu.features.reader

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.charliesbot.kanshu.core.ui.components.KanshuBottomSheet
import com.charliesbot.kanshu.core.ui.components.KanshuScaffold
import com.charliesbot.kanshu.core.ui.components.KanshuText
import com.charliesbot.kanshu.core.ui.theme.KanshuTheme
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun ReaderScreen(
  seriesId: Int,
  title: String,
  viewModel: ReaderViewModel = koinViewModel { parametersOf(seriesId) },
) {
  val uiState by viewModel.uiState.collectAsState()
  val chapterState by viewModel.chapterState.collectAsState()
  val remoteSuggestion by viewModel.remoteSuggestion.collectAsState()
  val preferences by viewModel.readerPreferences.collectAsState()

  var showOverlay by remember { mutableStateOf(false) }
  var showPrefsSheet by remember { mutableStateOf(false) }

  var lastLoadedPath by remember { mutableStateOf("") }
  var lastLoadedId by remember { mutableIntStateOf(-1) }

  KanshuScaffold {
    when (val state = uiState) {
      is ReaderUiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          KanshuText(text = "Loading...", style = KanshuTheme.typography.bodyLarge)
        }
      }
      is ReaderUiState.Error -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          val errorText =
            when (state) {
              ReaderUiState.Error.NotFound -> "Book not found."
              ReaderUiState.Error.ParseFailed -> "Failed to parse publication."
              ReaderUiState.Error.ReadFailed -> "Failed to read chapter."
            }
          KanshuText(text = errorText, style = KanshuTheme.typography.bodyLarge)
        }
      }
      is ReaderUiState.Ready -> {
        Box(modifier = Modifier.fillMaxSize()) {
          var webViewInstance by remember { mutableStateOf<WebView?>(null) }

          // Programmatic horizontal scrolls command evaluation
          LaunchedEffect(viewModel, webViewInstance) {
            val wv = webViewInstance ?: return@LaunchedEffect
            viewModel.evaluateJs.collectLatest { js -> wv.evaluateJavascript(js, null) }
          }

          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
              WebView(context).apply {
                setBackgroundColor(Color.WHITE)
                settings.apply {
                  javaScriptEnabled = true
                  blockNetworkLoads = true
                  domStorageEnabled = true
                }

                // Register Javascript interface bridge
                addJavascriptInterface(
                  KanshuJsBridge { event -> viewModel.handleBridgeEvent(event) },
                  "kanshuBridge",
                )

                // Intercept hardware page turn and volume buttons
                setOnKeyListener { _, keyCode, keyEvent ->
                  if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                      KeyEvent.KEYCODE_PAGE_UP,
                      KeyEvent.KEYCODE_VOLUME_UP -> {
                        viewModel.goBackward()
                        true
                      }
                      KeyEvent.KEYCODE_PAGE_DOWN,
                      KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        viewModel.goForward()
                        true
                      }
                      else -> false
                    }
                  } else {
                    false
                  }
                }

                // Touch listener to consume horizontal drags and handle margin taps
                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                var startX = 0f
                var startY = 0f
                var isClick = false

                setOnTouchListener { view, motionEvent ->
                  when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                      startX = motionEvent.x
                      startY = motionEvent.y
                      isClick = true
                      view.onTouchEvent(motionEvent)
                    }
                    MotionEvent.ACTION_MOVE -> {
                      val dx = abs(motionEvent.x - startX)
                      val dy = abs(motionEvent.y - startY)
                      if (dx > touchSlop || dy > touchSlop) {
                        isClick = false
                      }
                      if (dx > dy && dx > touchSlop) {
                        // Consume horizontal scrolls entirely to prevent standard physics
                        true
                      } else {
                        view.onTouchEvent(motionEvent)
                      }
                    }
                    MotionEvent.ACTION_UP -> {
                      val dx = abs(motionEvent.x - startX)
                      val dy = abs(motionEvent.y - startY)
                      if (isClick && dx < touchSlop && dy < touchSlop) {
                        val x = motionEvent.x
                        val width = view.width
                        val leftZone = width * 0.15f
                        val rightZone = width * 0.85f

                        if (x < leftZone) {
                          viewModel.goBackward()
                        } else if (x > rightZone) {
                          viewModel.goForward()
                        } else {
                          showOverlay = !showOverlay
                        }
                        true
                      } else {
                        view.onTouchEvent(motionEvent)
                      }
                    }
                    else -> view.onTouchEvent(motionEvent)
                  }
                }

                webViewInstance = this
                requestFocus()
              }
            },
            update = { webView ->
              val currentChapter = state.currentChapter

              if (currentChapter.path != lastLoadedPath || currentChapter.loadId != lastLoadedId) {
                lastLoadedPath = currentChapter.path
                lastLoadedId = currentChapter.loadId

                // Bind new client instance for chapter isolation
                webView.webViewClient =
                  KanshuWebViewClient(
                    context = webView.context,
                    publication = state.publication,
                    readLock = viewModel.readLock,
                    currentChapter = currentChapter,
                  )

                val chapterUrl =
                  "https://kanshu.invalid/${currentChapter.path}?__kanshu_load=${currentChapter.loadId}"
                webView.loadUrl(chapterUrl)
              }
            },
            onRelease = { webView ->
              webView.apply {
                stopLoading()
                loadUrl("about:blank")
                removeJavascriptInterface("kanshuBridge")
                setOnTouchListener(null)
                setOnKeyListener(null)
                webViewClient = android.webkit.WebViewClient()
              }
              webViewInstance = null
            },
          )

          // Navigation overlay shown on center zone tap
          if (showOverlay) {
            val currentSpineIndex = state.currentChapter.spineIndex

            ReaderOverlay(
              title = state.title.orEmpty(),
              chapterTitle = chapterState.title,
              prevChapterEnabled = currentSpineIndex > 0,
              nextChapterEnabled = currentSpineIndex < state.publication.readingOrder.size - 1,
              onPrevChapter = {
                showOverlay = false
                viewModel.loadSpineChapter(currentSpineIndex - 1, 0)
              },
              onNextChapter = {
                showOverlay = false
                viewModel.loadSpineChapter(currentSpineIndex + 1, 0)
              },
              onSyncToFurthest = {
                showOverlay = false
                viewModel.syncToFurthestPageRead()
              },
              onOpenReaderPrefs = { showPrefsSheet = true },
              onDismiss = { showOverlay = false },
            )
          }

          // Spacing and Typography Bottom Sheet
          KanshuBottomSheet(isOpen = showPrefsSheet, onDismiss = { showPrefsSheet = false }) {
            ReaderPrefsBottomSheet(
              prefs = preferences,
              callbacks =
                ReaderPrefsCallbacks(
                  onFontChange = viewModel::setFont,
                  onFontScaleChange = viewModel::setFontScale,
                  onMarginsChange = viewModel::setMargins,
                  onAlignmentChange = viewModel::setAlignment,
                  onLineSpacingChange = viewModel::setLineSpacing,
                  onParagraphSpacingChange = viewModel::setParagraphSpacing,
                  onWordSpacingChange = viewModel::setWordSpacing,
                  onLetterSpacingChange = viewModel::setLetterSpacing,
                  onResetSpacing = viewModel::resetSpacing,
                ),
            )
          }

          // Dialog for remote cloud progress suggestion
          remoteSuggestion?.let { suggestion ->
            RemoteProgressPrompt(
              suggestion = suggestion,
              onApply = viewModel::acceptRemoteSuggestion,
              onDismiss = viewModel::dismissRemoteSuggestion,
            )
          }
        }
      }
    }
  }
}
