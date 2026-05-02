package app.invigilator.ui.linking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.linking.LinkingError
import app.invigilator.core.linking.LinkingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LinkingCompletionUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val navigateToParentHome: Boolean = false,
)

@HiltViewModel
class LinkingCompletionViewModel @Inject constructor(
    private val linkingRepository: LinkingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val studentUid: String = checkNotNull(savedStateHandle["studentUid"]) {
        "LinkingCompletionViewModel requires 'studentUid' in SavedStateHandle"
    }
    private val consentId: String = checkNotNull(savedStateHandle["consentId"]) {
        "LinkingCompletionViewModel requires 'consentId' in SavedStateHandle"
    }

    private val _uiState = MutableStateFlow(LinkingCompletionUiState())
    val uiState: StateFlow<LinkingCompletionUiState> = _uiState.asStateFlow()

    init {
        completeLinking()
    }

    fun retry() {
        completeLinking()
    }

    fun clearNavigate() {
        _uiState.update { it.copy(navigateToParentHome = false) }
    }

    private fun completeLinking() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = linkingRepository.completeLinking(studentUid, consentId)

            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, navigateToParentHome = true) }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = result.exceptionOrNull().toUserMessage())
                }
            }
        }
    }

    private fun Throwable?.toUserMessage(): String = when (this) {
        is LinkingError.SessionExpired ->
            "Your linking session has expired. Please ask the student for a new code and try again."
        is LinkingError.AlreadyLinked ->
            "This student is already linked to a parent. If this is wrong, contact support."
        else ->
            "Could not complete linking. Please try again."
    }
}
