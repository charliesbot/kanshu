package com.charliesbot.kanshu.features.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charliesbot.kanshu.core.reader.ReaderResult
import com.charliesbot.kanshu.core.reader.usecase.OpenBookUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(private val seriesId: Int, private val openBook: OpenBookUseCase) :
  ViewModel() {
  private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
  val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

  private var publication: Publication? = null

  init {
    viewModelScope.launch {
      _uiState.value =
        when (val result = openBook(seriesId)) {
          is ReaderResult.Success -> {
            publication = result.publication
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

  override fun onCleared() {
    publication?.close()
    publication = null
  }
}
