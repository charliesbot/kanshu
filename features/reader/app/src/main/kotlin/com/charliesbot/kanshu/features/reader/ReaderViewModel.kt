package com.charliesbot.kanshu.features.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.reader.ReaderAlignment
import com.charliesbot.kanshu.core.reader.ReaderFont
import com.charliesbot.kanshu.core.reader.ReaderMargins
import com.charliesbot.kanshu.core.reader.ReaderPreferences
import com.charliesbot.kanshu.core.reader.ReaderPreferencesRepository
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import com.charliesbot.kanshu.core.sync.InitialPosition
import com.charliesbot.kanshu.core.sync.RemoteProgress
import com.charliesbot.kanshu.core.sync.SyncRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(
  private val seriesId: Int,
  private val openBook: OpenBookUseCase,
  private val sync: SyncRepository,
  private val preferences: ReaderPreferencesRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
  val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

  private var publication: Publication? = null
  private var bookFile: File? = null
  private var tocIndex: TocIndex? = null
  private val bookId: String = "kavita:$seriesId"

  private val _currentLocator = MutableStateFlow<Locator?>(null)

  // The "Continue from page X on (device)?" prompt. Non-null while the dialog is showing;
  // the screen observes this and renders or hides the dialog accordingly.
  private val _remoteSuggestion = MutableStateFlow<RemoteProgress?>(null)
  val remoteSuggestion: StateFlow<RemoteProgress?> = _remoteSuggestion.asStateFlow()

  // One-shot navigation commands for the screen to forward to the Readium navigator. Used by
  // the prompt's Apply action and by the manual "Sync to Furthest Page Read" menu item.
  // SharedFlow with a small buffer survives a slow collector during config change.
  private val _navigateTo = MutableSharedFlow<Locator>(extraBufferCapacity = 1)
  val navigateTo: SharedFlow<Locator> = _navigateTo.asSharedFlow()

  // One-shot "manual sync didn't find a further position" feedback for the screen to surface
  // as a toast or similar. Same buffering rationale as navigateTo.
  private val _alreadyAtFurthest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  val alreadyAtFurthest: SharedFlow<Unit> = _alreadyAtFurthest.asSharedFlow()

  val chapterState: StateFlow<ChapterState> =
    _currentLocator
      .map { locator -> tocIndex?.chapterStateFor(locator) ?: ChapterState.Empty }
      .stateIn(viewModelScope, SharingStarted.Eagerly, ChapterState.Empty)

  // Live prefs surfaced to the UI. Eagerly started so the bottom sheet shows the persisted
  // values immediately when opened, without a per-collector cold start. WhileSubscribed isn't
  // worth the cost here — there's only ever one screen observing.
  val readerPreferences: StateFlow<ReaderPreferences> =
    preferences.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderPreferences())

  init {
    viewModelScope.launch {
      // Resolve persisted preferences before mounting the navigator so the initial frame already
      // uses the stored font/scale. On a warm DataStore this is a single in-memory read.
      val storedPrefs = preferences.preferences.first()
      when (val result = openBook(seriesId)) {
        is ReaderResult.Success -> {
          publication = result.publication
          bookFile = result.file
          tocIndex = TocIndex(result.publication)
          val initial = sync.resolveInitialPosition(bookId, result.file, result.publication)
          val (initialLocator, remote) =
            when (initial) {
              is InitialPosition.UseLocal -> initial.locator to null
              is InitialPosition.PromptForRemote -> initial.local to initial.remote
            }
          _remoteSuggestion.value = remote
          _uiState.value =
            ReaderUiState.Ready(
              title = result.publication.metadata.title,
              publication = result.publication,
              initialLocator = initialLocator,
              initialPreferences = storedPrefs,
            )
        }
        ReaderResult.Error.NotFound -> _uiState.value = ReaderUiState.Error.NotFound
        ReaderResult.Error.ParseFailed -> _uiState.value = ReaderUiState.Error.ParseFailed
        ReaderResult.Error.ReadFailed -> _uiState.value = ReaderUiState.Error.ReadFailed
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

  fun onLocatorChanged(locator: Locator) {
    _currentLocator.value = locator
    val pub = publication ?: return
    val file = bookFile ?: return
    sync.setProgress(bookId, file, locator, pub)
  }

  fun acceptRemoteSuggestion() {
    val remote = _remoteSuggestion.value ?: return
    _remoteSuggestion.value = null
    _navigateTo.tryEmit(remote.locator)
  }

  fun dismissRemoteSuggestion() {
    _remoteSuggestion.value = null
  }

  fun syncToFurthestPageRead() {
    val pub = publication ?: return
    val file = bookFile ?: return
    viewModelScope.launch {
      val remote = sync.pullFurthestPosition(bookId, file, pub)
      if (remote != null) _navigateTo.tryEmit(remote.locator) else _alreadyAtFurthest.tryEmit(Unit)
    }
  }

  override fun onCleared() {
    val pub = publication ?: return
    val file = bookFile
    val current = _currentLocator.value
    publication = null
    bookFile = null
    // viewModelScope is already cancelled. Use a fresh scope so the flush + close survive long
    // enough to complete. flushProgress has its own internal timeout; pub.close runs after.
    CoroutineScope(Dispatchers.IO).launch {
      if (file != null && current != null) {
        sync.flushProgress(bookId, file, current, pub)
      }
      pub.close()
    }
  }
}
