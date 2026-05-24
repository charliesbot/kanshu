package com.charliesbot.kanshu.features.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderMargins
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.reader.ReaderPreferencesRepository
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import com.charliesbot.kanshu.core.sync.InitialPosition
import com.charliesbot.kanshu.core.sync.RemoteProgress
import com.charliesbot.kanshu.core.sync.SyncRepository
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(
  private val seriesId: Int,
  private val openBook: OpenBookUseCase,
  private val sync: SyncRepository,
  private val preferences: ReaderPreferencesRepository,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
  private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
  val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

  private var publication: Publication? = null
  private var bookFile: File? = null
  private var tocIndex: TocIndex? = null
  private val bookId: String = "kavita:$seriesId"

  private val _currentPosition = MutableStateFlow<ReaderPosition?>(null)

  // The "Continue from page X on (device)?" prompt. Non-null while the dialog is showing;
  // the screen observes this and renders or hides the dialog accordingly.
  private val _remoteSuggestion = MutableStateFlow<RemoteProgress?>(null)
  val remoteSuggestion: StateFlow<RemoteProgress?> = _remoteSuggestion.asStateFlow()

  // One-shot navigation commands for the screen to forward to the reader. Used by
  // the prompt's Apply action and by the manual "Sync to Furthest Page Read" menu item.
  // SharedFlow with a small buffer survives a slow collector during config change.
  private val _navigateTo = MutableSharedFlow<ReaderPosition>(extraBufferCapacity = 1)
  val navigateTo: SharedFlow<ReaderPosition> = _navigateTo.asSharedFlow()

  // Emission flow for JS horizontal scrolling statements (e.g. kanshu.scrollToPage)
  private val _evaluateJs = MutableSharedFlow<String>(extraBufferCapacity = 1)
  val evaluateJs: SharedFlow<String> = _evaluateJs.asSharedFlow()

  // Current page indices and counts mapped from bridge reports
  private val _pageCount = MutableStateFlow(1)
  val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

  private val _pageIndex = MutableStateFlow(0)
  val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

  val readLock = Mutex()
  private val activeChapterLoadId = AtomicInteger(0)
  private var activeLoadJob: Job? = null

  // One-shot "manual sync didn't find a further position" feedback for the screen to surface
  // as a toast or similar. Same buffering rationale as navigateTo.
  private val _alreadyAtFurthest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val alreadyAtFurthest: SharedFlow<Unit> = _alreadyAtFurthest.asSharedFlow()

  val chapterState: StateFlow<ChapterState> =
    _currentPosition
      .map { position ->
        val locator =
          position?.let { pos ->
            publication?.readingOrder?.getOrNull(pos.spineIndex)?.let { link ->
              publication?.locatorFromLink(link)
            }
          }
        tocIndex?.chapterStateFor(locator) ?: ChapterState.Empty
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, ChapterState.Empty)

  // Live prefs surfaced to the UI. Eagerly started so the bottom sheet shows the persisted
  // values immediately when opened, without a per-collector cold start. WhileSubscribed isn't
  // worth the cost here — there's only ever one screen observing.
  val readerPreferences: StateFlow<ReaderPreferences> =
    preferences.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderPreferences())

  init {
    activeLoadJob =
      viewModelScope.launch {
        // Resolve persisted preferences before mounting the reader so the initial frame already
        // uses the stored font/scale. On a DataStore this is a single in-memory read.
        val storedPrefs = preferences.preferences.first()
        when (val result = openBook(seriesId)) {
          is ReaderResult.Success -> {
            publication = result.publication
            bookFile = result.file
            tocIndex = TocIndex(result.publication)
            val initial = sync.resolveInitialPosition(bookId, result.file, result.publication)
            val (initialPosition, remote) =
              when (initial) {
                is InitialPosition.UseLocal -> initial.position to null
                is InitialPosition.PromptForRemote -> initial.local to initial.remote
              }
            _remoteSuggestion.value = remote

            val pos =
              initialPosition
                ?: ReaderPosition(
                  schemaVersion = 1,
                  spineIndex = 0,
                  pageIndex = 0,
                  progressInSpine = 0.0f,
                )
            val spineIndex = pos.spineIndex
            val link = result.publication.readingOrder.getOrNull(spineIndex)
            if (link == null) {
              _uiState.value = ReaderUiState.Error.ReadFailed
              return@launch
            }

            val chapter = loadAndSanitizeChapter(result.publication, link, spineIndex)
            if (chapter == null) {
              _uiState.value = ReaderUiState.Error.ReadFailed
              return@launch
            }

            _currentPosition.value = pos
            _pageIndex.value = pos.pageIndex
            _uiState.value =
              ReaderUiState.Ready(
                title = result.publication.metadata.title,
                publication = result.publication,
                initialPosition = pos,
                initialPreferences = storedPrefs,
                currentChapter = chapter,
              )
          }
          ReaderResult.Error.NotFound -> _uiState.value = ReaderUiState.Error.NotFound
          ReaderResult.Error.ParseFailed -> _uiState.value = ReaderUiState.Error.ParseFailed
          ReaderResult.Error.ReadFailed -> _uiState.value = ReaderUiState.Error.ReadFailed
        }
      }
  }

  private suspend fun loadAndSanitizeChapter(
    pub: Publication,
    link: Link,
    spineIndex: Int,
  ): CachedResource? =
    withContext(ioDispatcher) {
      val resource = pub.get(link) ?: return@withContext null
      try {
        val rawBytes = readLock.withLock { resource.read().getOrNull() } ?: return@withContext null
        val rawHtml = String(rawBytes, Charsets.UTF_8)
        val sanitizedHtml = KanshuHtmlSanitizer.sanitizeAndWrap(rawHtml)
        val loadId = activeChapterLoadId.incrementAndGet()
        CachedResource(
          path = link.href.toString().removePrefix("/"),
          spineIndex = spineIndex,
          loadId = loadId,
          bytes = sanitizedHtml.toByteArray(Charsets.UTF_8),
          mimeType = link.mediaType?.toString() ?: "application/xhtml+xml",
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        null
      } finally {
        resource.close()
      }
    }

  fun loadSpineChapter(spineIndex: Int, targetPageIndex: Int) {
    val pub = publication ?: return
    activeLoadJob?.cancel()
    activeLoadJob =
      viewModelScope.launch {
        val link = pub.readingOrder.getOrNull(spineIndex) ?: return@launch
        val chapter = loadAndSanitizeChapter(pub, link, spineIndex)
        if (chapter == null) {
          _uiState.value = ReaderUiState.Error.ReadFailed
          return@launch
        }

        val storedPrefs = preferences.preferences.first()
        val initialPosition =
          ReaderPosition(
            schemaVersion = 1,
            spineIndex = spineIndex,
            pageIndex = targetPageIndex,
            progressInSpine = 0.0f,
          )

        _currentPosition.value = initialPosition
        _pageIndex.value = targetPageIndex
        _pageCount.value = 1
        _uiState.value =
          ReaderUiState.Ready(
            title = pub.metadata.title,
            publication = pub,
            initialPosition = initialPosition,
            initialPreferences = storedPrefs,
            currentChapter = chapter,
          )
      }
  }

  fun goForward() {
    val readyState = _uiState.value as? ReaderUiState.Ready ?: return
    val index = _pageIndex.value
    val count = _pageCount.value
    if (index < count - 1) {
      val target = index + 1
      _evaluateJs.tryEmit("kanshu.scrollToPage($target)")
    } else {
      val currentSpineIndex = readyState.currentChapter.spineIndex
      if (currentSpineIndex < readyState.publication.readingOrder.size - 1) {
        loadSpineChapter(currentSpineIndex + 1, targetPageIndex = 0)
      }
    }
  }

  fun goBackward() {
    val readyState = _uiState.value as? ReaderUiState.Ready ?: return
    val index = _pageIndex.value
    if (index > 0) {
      val target = index - 1
      _evaluateJs.tryEmit("kanshu.scrollToPage($target)")
    } else {
      val currentSpineIndex = readyState.currentChapter.spineIndex
      if (currentSpineIndex > 0) {
        // Pass 9999 to automatically clamp to the last page after repagination
        loadSpineChapter(currentSpineIndex - 1, targetPageIndex = 9999)
      }
    }
  }

  fun handleBridgeEvent(event: BridgeEvent) {
    val readyState = _uiState.value as? ReaderUiState.Ready ?: return
    val loadId =
      when (event) {
        is BridgeEvent.PageSettled -> event.chapterLoadId
        is BridgeEvent.Repaginated -> event.chapterLoadId
      }
    if (loadId != readyState.currentChapter.loadId) return

    when (event) {
      is BridgeEvent.PageSettled -> {
        _pageIndex.value = event.pageIndex
        val currentSpineIndex = readyState.currentChapter.spineIndex
        val position =
          ReaderPosition(
            schemaVersion = 1,
            spineIndex = currentSpineIndex,
            pageIndex = event.pageIndex,
            progressInSpine = event.progressInSpine,
          )
        onPositionChanged(position)
      }
      is BridgeEvent.Repaginated -> {
        _pageCount.value = event.pageCount
        _pageIndex.value = event.restoredPageIndex
        val currentSpineIndex = readyState.currentChapter.spineIndex

        val progress =
          if (event.pageCount > 1) {
            event.restoredPageIndex.toFloat() / (event.pageCount - 1)
          } else {
            0.0f
          }

        val position =
          ReaderPosition(
            schemaVersion = 1,
            spineIndex = currentSpineIndex,
            pageIndex = event.restoredPageIndex,
            progressInSpine = progress,
          )
        onPositionChanged(position)
      }
    }
  }

  fun setFont(font: ReaderFont) {
    viewModelScope.launch { preferences.setFont(font) }
  }

  fun setFontScale(scale: Float) {
    viewModelScope.launch { preferences.setFontScale(scale) }
  }

  fun setMargins(margins: ReaderMargins) {
    viewModelScope.launch { preferences.setMargins(margins) }
  }

  fun setAlignment(alignment: ReaderAlignment) {
    viewModelScope.launch { preferences.setAlignment(alignment) }
  }

  fun setLineSpacing(value: Float) {
    viewModelScope.launch { preferences.setLineSpacing(value) }
  }

  fun setParagraphSpacing(value: Float) {
    viewModelScope.launch { preferences.setParagraphSpacing(value) }
  }

  fun setWordSpacing(value: Float) {
    viewModelScope.launch { preferences.setWordSpacing(value) }
  }

  fun setLetterSpacing(value: Float) {
    viewModelScope.launch { preferences.setLetterSpacing(value) }
  }

  fun resetSpacing() {
    viewModelScope.launch { preferences.resetSpacing() }
  }

  fun onPositionChanged(position: ReaderPosition) {
    _currentPosition.value = position
    val pub = publication ?: return
    val file = bookFile ?: return
    sync.setProgress(bookId, file, position, pub)
  }

  fun acceptRemoteSuggestion() {
    val remote = _remoteSuggestion.value ?: return
    _remoteSuggestion.value = null
    _navigateTo.tryEmit(remote.position)
  }

  fun dismissRemoteSuggestion() {
    _remoteSuggestion.value = null
  }

  fun syncToFurthestPageRead() {
    val pub = publication ?: return
    val file = bookFile ?: return
    viewModelScope.launch {
      val remote = sync.pullFurthestPosition(bookId, file, pub)
      if (remote != null) _navigateTo.tryEmit(remote.position) else _alreadyAtFurthest.tryEmit(Unit)
    }
  }

  override fun onCleared() {
    val pub = publication ?: return
    val file = bookFile
    val current = _currentPosition.value
    publication = null
    bookFile = null
    // viewModelScope is already cancelled. Use a fresh scope so the flush + close survive long
    // enough to complete. flushProgress has its own internal timeout; pub.close runs after.
    CoroutineScope(ioDispatcher).launch {
      if (file != null && current != null) {
        sync.flushProgress(bookId, file, current, pub)
      }
      pub.close()
    }
  }
}
