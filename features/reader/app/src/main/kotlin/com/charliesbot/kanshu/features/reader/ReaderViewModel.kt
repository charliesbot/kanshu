package com.charliesbot.kanshu.features.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(private val seriesId: Int, private val openBook: OpenBookUseCase) :
  ViewModel() {
  private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
  val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

  private var publication: Publication? = null
  private var tocIndex: TocIndex? = null

  private val _currentLocator = MutableStateFlow<Locator?>(null)

  val chapterState: StateFlow<ChapterState> =
    _currentLocator
      .map { locator -> tocIndex?.chapterStateFor(locator) ?: ChapterState.Empty }
      .stateIn(viewModelScope, SharingStarted.Eagerly, ChapterState.Empty)

  init {
    viewModelScope.launch {
      _uiState.value =
        when (val result = openBook(seriesId)) {
          is ReaderResult.Success -> {
            publication = result.publication
            tocIndex = TocIndex(result.publication)
            // EpubNavigatorFactory is a thin data holder over Publication + configuration; the
            // heavy lifting (manifest walk, CSS injectables) happens inside the FragmentFactory's
            // instantiate path during commitNow, which the FragmentManager runs on Main anyway.
            // Constructing the factory here is cheap.
            ReaderUiState.Ready(
              title = result.publication.metadata.title,
              factory =
                EpubNavigatorFactory(
                  publication = result.publication,
                  configuration =
                    EpubNavigatorFactory.Configuration(defaults = EpubTypography.defaults),
                ),
            )
          }
          ReaderResult.Error.NotFound -> ReaderUiState.Error.NotFound
          ReaderResult.Error.ParseFailed -> ReaderUiState.Error.ParseFailed
          ReaderResult.Error.ReadFailed -> ReaderUiState.Error.ReadFailed
        }
    }
  }

  fun onLocatorChanged(locator: Locator) {
    _currentLocator.value = locator
  }

  override fun onCleared() {
    val pub = publication ?: return
    publication = null
    // Publication.close() releases container/asset handles via blocking I/O. onCleared runs on
    // Main and viewModelScope is already cancelled here, so fire-and-forget on IO.
    CoroutineScope(Dispatchers.IO).launch { pub.close() }
  }
}
