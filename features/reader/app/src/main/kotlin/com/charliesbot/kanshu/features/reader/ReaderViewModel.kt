package com.charliesbot.kanshu.features.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.library.BookIds
import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderHighlightColor
import com.charliesbot.kanshu.core.reader.ReaderMargins
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.reader.ReaderPreferencesRepository
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.annotation.AnnotationRepository
import com.charliesbot.kanshu.core.reader.progress.ReaderPosition
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import com.charliesbot.kanshu.core.sync.SyncRepository
import com.charliesbot.kanshu.navigator.ReaderHighlight
import com.charliesbot.kanshu.navigator.ReaderPagePositions
import com.charliesbot.kanshu.navigator.ReaderResourceLoader
import com.charliesbot.kanshu.navigator.ReaderSelectionInfo
import com.charliesbot.kanshu.navigator.model.ParseDiagnostics
import com.charliesbot.kanshu.navigator.model.ReaderDocument
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication

sealed interface ReaderUiState {
  data object Loading : ReaderUiState

  data class Reading(
    val chapterToken: Long,
    val spineIndex: Int,
    val document: ReaderDocument,
    val diagnostics: ParseDiagnostics,
  ) : ReaderUiState

  sealed interface Error : ReaderUiState {
    data object NotFound : Error

    data object OpenFailed : Error
  }
}

class ReaderViewModel(
  private val openBook: OpenBookUseCase,
  private val preferencesRepository: ReaderPreferencesRepository,
  private val syncRepository: SyncRepository,
  private val annotationRepository: AnnotationRepository,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
  private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
  val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

  /**
   * Applied reader typography. Starts from [ReaderPreferences] defaults so the first frame renders
   * well with no configuration; the repository emits the persisted values (which also default
   * field-by-field for anything never set).
   */
  val preferences: StateFlow<ReaderPreferences> =
    preferencesRepository.preferences.stateIn(
      viewModelScope,
      SharingStarted.Eagerly,
      ReaderPreferences(),
    )

  fun setFont(font: ReaderFont) {
    viewModelScope.launch { preferencesRepository.setFont(font) }
  }

  fun setFontScale(scale: Float) {
    viewModelScope.launch { preferencesRepository.setFontScale(scale) }
  }

  fun setBoldness(value: Float) {
    viewModelScope.launch { preferencesRepository.setBoldness(value) }
  }

  fun setMargins(margins: ReaderMargins) {
    viewModelScope.launch { preferencesRepository.setMargins(margins) }
  }

  fun setAlignment(alignment: ReaderAlignment) {
    viewModelScope.launch { preferencesRepository.setAlignment(alignment) }
  }

  fun setLineSpacing(value: Float) {
    viewModelScope.launch { preferencesRepository.setLineSpacing(value) }
  }

  fun setParagraphSpacing(value: Float) {
    viewModelScope.launch { preferencesRepository.setParagraphSpacing(value) }
  }

  fun setWordSpacing(value: Float) {
    viewModelScope.launch { preferencesRepository.setWordSpacing(value) }
  }

  fun setLetterSpacing(value: Float) {
    viewModelScope.launch { preferencesRepository.setLetterSpacing(value) }
  }

  fun resetSpacing() {
    viewModelScope.launch { preferencesRepository.resetSpacing() }
  }

  private val _currentPage = MutableStateFlow(0)
  val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

  private val _pageCount = MutableStateFlow(0)
  val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

  private val _resourceLoader = MutableStateFlow<ReaderResourceLoader?>(null)
  val resourceLoader: StateFlow<ReaderResourceLoader?> = _resourceLoader.asStateFlow()

  private val _highlights = MutableStateFlow<List<ReaderHighlight>>(emptyList())

  /**
   * Highlights for the chapter on screen, in chapter text-stream offsets.
   *
   * Re-subscribed per chapter rather than held per book, so the renderer never has to filter, and
   * emptied on every chapter change so the previous chapter's marks can't paint on the new one.
   */
  val highlights: StateFlow<List<ReaderHighlight>> = _highlights.asStateFlow()
  private var highlightsJob: Job? = null

  private enum class LandingPage {
    Start,
    End,
  }

  private sealed interface BookLifecycle {
    data object Empty : BookLifecycle

    data class Opening(val token: Long, val seriesId: Int) : BookLifecycle

    data class Open(val session: BookSession) : BookLifecycle
  }

  private data class BookSession(
    val seriesId: Int,
    val bookId: String,
    val file: File,
    val publication: Publication,
    val resourceLoader: ReaderResourceLoader,
    val stylesheets: PublicationStylesheets,
    val spineItems: MutableMap<Int, SpineItem> = mutableMapOf(),
  )

  private var openJob: Job? = null
  private var spineJob: Job? = null
  private var bookLifecycle: BookLifecycle = BookLifecycle.Empty
  private var nextBookToken = 0L
  private var nextChapterToken = 0L
  private var currentSpineIndex = -1
  // Char offsets of the current chapter's pages. Empty until its layout reports them, which is
  // why progress is only written once positions arrive rather than on every page-index change.
  private var pagePositions = ReaderPagePositions.Empty
  // The character offset the reader is holding in the current chapter. Seeded from the restored
  // position, re-resolved to a page on every pagination, and updated on every page turn. Null
  // only between opening a chapter and its first layout.
  private var anchorCharOffset: Int? = null
  private val openSession: BookSession?
    get() = (bookLifecycle as? BookLifecycle.Open)?.session

  private fun lastPageIndex(): Int = (_pageCount.value - 1).coerceAtLeast(0)

  fun open(seriesId: Int) {
    val activeSeriesId =
      when (val lifecycle = bookLifecycle) {
        BookLifecycle.Empty -> null
        is BookLifecycle.Opening -> lifecycle.seriesId
        is BookLifecycle.Open -> lifecycle.session.seriesId
      }
    if (activeSeriesId == seriesId) return

    val opening = BookLifecycle.Opening(token = ++nextBookToken, seriesId = seriesId)
    openJob?.cancel()
    spineJob?.cancel()
    openSession?.publication?.close()
    bookLifecycle = opening
    _uiState.value = ReaderUiState.Loading
    _currentPage.value = 0
    _pageCount.value = 0
    spineJob = null
    _resourceLoader.value = null
    currentSpineIndex = -1
    pagePositions = ReaderPagePositions.Empty
    anchorCharOffset = null
    highlightsJob?.cancel()
    highlightsJob = null
    _highlights.value = emptyList()

    openJob = viewModelScope.launch {
      val unownedPublication = AtomicReference<Publication?>()
      try {
        Log.d(TAG, "open($seriesId): loading")
        val result =
          withContext(ioDispatcher) {
            openBook(seriesId).also { opened ->
              unownedPublication.set((opened as? ReaderResult.Success)?.publication)
            }
          }
        if (bookLifecycle != opening) return@launch
        when (result) {
          is ReaderResult.Success -> {
            val session =
              BookSession(
                seriesId = seriesId,
                bookId = BookIds.forKavitaSeries(seriesId),
                file = result.file,
                publication = result.publication,
                resourceLoader = PublicationResourceLoader(result.publication),
                stylesheets = PublicationStylesheets(result.publication),
              )
            Log.d(
              TAG,
              "open($seriesId): publication opened, spine=${result.publication.readingOrder.size}",
            )
            val restored = restoredPosition(session.bookId)
            anchorCharOffset = restored?.charOffset
            val spineItem =
              withContext(ioDispatcher) {
                val stored =
                  restored?.spineIndex?.let {
                    session.publication.readSpineItemAt(it, session.stylesheets)
                  }
                // An unreadable stored chapter (book replaced, spine reordered) falls back to the
                // start rather than refusing to open the book.
                //
                // TODO: this fallback is silent, and the first layout then reports the book start,
                // which overwrites the stored position. Acceptable for now because the case that
                // reaches here is almost always a changed spine, where the stored offset no longer
                // means anything. The fix is to surface the failed resume instead of guessing: show
                // "couldn't open where you left off", and let the user's choice create the
                // position.
                stored
                  ?: session.publication.readNextSpineItem(
                    afterSpineIndex = -1,
                    session.stylesheets,
                  )
              }
            if (bookLifecycle != opening) {
              return@launch
            }
            if (spineItem == null) {
              bookLifecycle = BookLifecycle.Empty
              failOpen("open($seriesId): no spine item → OpenFailed")
              return@launch
            }
            if (spineItem.spineIndex != restored?.spineIndex) {
              anchorCharOffset = null
            }
            bookLifecycle = BookLifecycle.Open(session)
            unownedPublication.set(null)
            _resourceLoader.value = session.resourceLoader
            Log.d(TAG, "open($seriesId): Reading with ${spineItem.document.blocks.size} blocks")
            activateSpineItem(session, spineItem)
          }
          ReaderResult.Error.NotFound -> {
            bookLifecycle = BookLifecycle.Empty
            Log.d(TAG, "open($seriesId): NotFound")
            _uiState.value = ReaderUiState.Error.NotFound
          }
          ReaderResult.Error.ParseFailed -> {
            bookLifecycle = BookLifecycle.Empty
            failOpen("open($seriesId): ParseFailed → OpenFailed")
          }
          ReaderResult.Error.ReadFailed -> {
            bookLifecycle = BookLifecycle.Empty
            failOpen("open($seriesId): ReadFailed → OpenFailed")
          }
        }
      } finally {
        unownedPublication.getAndSet(null)?.close()
      }
    }
  }

  fun onPageCount(chapterToken: Long, count: Int) {
    if (!isCurrentChapter(chapterToken)) {
      Log.d(TAG, "onPageCount($count) ignored for stale chapter[$chapterToken]")
      return
    }
    Log.d(TAG, "onPageCount($count)")
    _pageCount.value = count
    _currentPage.update { page -> page.coerceIn(0, lastPageIndex()) }
  }

  /**
   * Receives the chapter's page-to-character-offset table once layout completes.
   *
   * Every pagination re-resolves [anchorCharOffset] to a page, not just the first one after a
   * restore. A preference change repaginates the chapter, and holding the old page *index* against
   * a new pagination would land the reader on different text — the exact failure character offsets
   * exist to prevent. Once anchored, the reader keeps its place across font size, margins, and
   * spacing.
   */
  fun onPagePositions(chapterToken: Long, positions: ReaderPagePositions) {
    if (!isCurrentChapter(chapterToken)) {
      Log.d(TAG, "onPagePositions ignored for stale chapter[$chapterToken]")
      return
    }
    pagePositions = positions
    val anchor = anchorCharOffset
    if (anchor != null) {
      val page = positions.pageIndexOf(anchor)
      Log.d(TAG, "anchor charOffset=$anchor → page $page")
      _currentPage.value = page
    } else if (positions.pageCount > 0) {
      // First pagination of a freshly opened chapter with nothing to restore: adopt the page we
      // landed on (0, or the last page when paging backward across a boundary) as the anchor.
      anchorCharOffset =
        positions.charOffsetOf(_currentPage.value.coerceIn(0, positions.pageCount - 1))
    }
    persistProgress()
  }

  /**
   * Stores the current selection as a highlight. The range is the engine's, in chapter text-stream
   * offsets, so the highlight lands on the same words after any repagination.
   */
  fun addHighlight(
    selection: ReaderSelectionInfo,
    color: ReaderHighlightColor = ReaderHighlightColor.default,
  ) {
    val id = openSession?.bookId ?: return
    if (!selection.hasRange) return
    val spineIndex = currentSpineIndex
    viewModelScope.launch {
      annotationRepository.addHighlight(
        bookId = id,
        spineIndex = spineIndex,
        startCharOffset = selection.startCharOffset,
        endCharOffset = selection.endCharOffset,
        selectedText = selection.text,
        color = color,
      )
    }
  }

  fun removeHighlight(id: String) {
    viewModelScope.launch { annotationRepository.delete(id) }
  }

  fun setHighlightColor(id: String, color: ReaderHighlightColor) {
    viewModelScope.launch { annotationRepository.updateHighlightColor(id, color) }
  }

  private fun observeHighlights(chapterToken: Long) {
    val id = openSession?.bookId ?: return
    val spineIndex = currentSpineIndex
    highlightsJob?.cancel()
    _highlights.value = emptyList()
    highlightsJob = viewModelScope.launch {
      annotationRepository.observeForSpine(id, spineIndex).collect { annotations ->
        if (!isCurrentChapter(chapterToken)) return@collect
        _highlights.value = annotations.map {
          ReaderHighlight(
            startCharOffset = it.startCharOffset,
            endCharOffset = it.endCharOffset,
            id = it.id,
            color = it.color,
          )
        }
      }
    }
  }

  /** Re-anchors to the page the reader just moved to, then persists it. */
  private fun onPageChanged() {
    if (pagePositions.pageCount == 0) return
    val page = _currentPage.value.coerceIn(0, pagePositions.pageCount - 1)
    anchorCharOffset = pagePositions.charOffsetOf(page)
    persistProgress()
  }

  /**
   * The position to reopen at, or null to start from the beginning.
   *
   * Read from the local row only. Reconciling with a newer remote needs the "continue from
   * (device)?" prompt, and consulting the server here would put an HTTP timeout in front of the
   * first page on a device that is regularly offline.
   */
  private suspend fun restoredPosition(bookId: String): ReaderPosition? =
    syncRepository.localPosition(bookId)

  /**
   * Reports the current page's character offset to the sync layer, which persists locally at once
   * and debounces the remote push on its own scope — so progress survives this ViewModel being torn
   * down without a teardown flush of our own.
   *
   * Called freely, including on the pagination that follows a resume. Deciding what is worth
   * writing and what would overwrite a further-along remote is the sync layer's job.
   */
  private fun persistProgress() {
    val session = openSession ?: return
    if (pagePositions.pageCount == 0 || currentSpineIndex < 0) return
    val page = _currentPage.value.coerceIn(0, pagePositions.pageCount - 1)
    val position =
      ReaderPosition(
        spineIndex = currentSpineIndex,
        charOffset = pagePositions.charOffsetOf(page),
        progressInSpine = pagePositions.progressInSpine(page),
      )
    syncRepository.setProgress(
      bookId = session.bookId,
      file = session.file,
      position = position,
      publication = session.publication,
    )
  }

  fun onLayoutFailed(chapterToken: Long) {
    if (!isCurrentChapter(chapterToken)) return
    failOpen("onLayoutFailed → OpenFailed")
  }

  private fun isCurrentChapter(chapterToken: Long): Boolean =
    (_uiState.value as? ReaderUiState.Reading)?.chapterToken == chapterToken

  private fun failOpen(message: String) {
    Log.d(TAG, message)
    _uiState.value = ReaderUiState.Error.OpenFailed
  }

  fun nextPage() {
    if (_pageCount.value == 0) {
      Log.d(TAG, "nextPage ignored while page count is unknown")
      return
    }
    if (_currentPage.value >= lastPageIndex()) {
      openSpineItem(currentSpineIndex + 1, LandingPage.Start)
      return
    }
    _currentPage.update { page ->
      val next = (page + 1).coerceAtMost(lastPageIndex())
      Log.d(TAG, "nextPage: $page → $next (of ${_pageCount.value})")
      next
    }
    onPageChanged()
  }

  fun previousPage() {
    if (_pageCount.value == 0) {
      Log.d(TAG, "previousPage ignored while page count is unknown")
      return
    }
    if (_currentPage.value <= 0) {
      if (currentSpineIndex > 0) {
        openSpineItem(currentSpineIndex - 1, LandingPage.End)
      } else {
        Log.d(TAG, "previousPage: already at first spine item")
      }
      return
    }
    _currentPage.update { page ->
      val previous = page - 1
      Log.d(TAG, "previousPage: $page → $previous (of ${_pageCount.value})")
      previous
    }
    onPageChanged()
  }

  /**
   * Navigates to the spine item a publication-internal link points at. Fragments resolve to the
   * chapter start for now — anchor-to-page mapping is a later slice. Unresolvable hrefs and
   * same-chapter links are ignored.
   */
  fun openLink(href: String) {
    val path = href.substringBefore('#')
    val currentPublication = openSession?.publication ?: return
    val target =
      currentPublication.readingOrder.indexOfFirst { link ->
        link.url().path?.trimStart('/') == path
      }
    if (target == -1) {
      Log.d(TAG, "openLink: no spine item for $href")
      return
    }
    if (target == currentSpineIndex) {
      Log.d(TAG, "openLink: already on spine[$target], fragment navigation not yet supported")
      return
    }
    openSpineItem(target, LandingPage.Start)
  }

  private fun openSpineItem(targetSpineIndex: Int, landing: LandingPage) {
    if (spineJob?.isActive == true) return
    val session = openSession ?: return
    val startingSpineIndex = currentSpineIndex
    spineJob = viewModelScope.launch {
      try {
        val item =
          session.spineItems[targetSpineIndex]
            ?: withContext(ioDispatcher) {
              session.publication.readSpineItemAt(targetSpineIndex, session.stylesheets)
            }
        if (openSession !== session || currentSpineIndex != startingSpineIndex) {
          Log.d(TAG, "openSpineItem: ignored stale open of spine[$targetSpineIndex]")
          return@launch
        }
        if (item == null) {
          Log.d(TAG, "openSpineItem: spine[$targetSpineIndex] unavailable")
          return@launch
        }
        _pageCount.value = 0
        // Cleared before the new chapter reports its own table, so no offset from the previous
        // chapter can be persisted or re-anchored against this spine index.
        pagePositions = ReaderPagePositions.Empty
        anchorCharOffset = null
        _currentPage.value = if (landing == LandingPage.End) LAST_PAGE_SENTINEL else 0
        Log.d(TAG, "openSpineItem: opened spine[${item.spineIndex}] landing=$landing")
        activateSpineItem(session, item)
      } finally {
        val runningJob = currentCoroutineContext()[Job]
        if (spineJob === runningJob) {
          spineJob = null
        }
      }
    }
  }

  private fun activateSpineItem(session: BookSession, item: SpineItem) {
    session.spineItems[item.spineIndex] = item
    currentSpineIndex = item.spineIndex
    val chapterToken = ++nextChapterToken
    _uiState.value =
      ReaderUiState.Reading(
        chapterToken = chapterToken,
        spineIndex = item.spineIndex,
        document = item.document,
        diagnostics = item.diagnostics,
      )
    observeHighlights(chapterToken)
  }

  override fun onCleared() {
    highlightsJob?.cancel()
    openJob?.cancel()
    spineJob?.cancel()
    openSession?.publication?.close()
  }

  private companion object {
    const val TAG = "ReaderViewModel"
    // Placeholder page index meaning "last page of the chapter". Set when navigating backward
    // into a chapter whose page count is not known yet; onPageCount clamps it to the real last
    // page.
    const val LAST_PAGE_SENTINEL = Int.MAX_VALUE
  }
}
