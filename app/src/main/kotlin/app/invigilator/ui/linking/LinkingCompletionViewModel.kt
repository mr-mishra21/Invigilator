package app.invigilator.ui.linking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.linking.LinkingRepository
import app.invigilator.core.user.LinkedStudentDoc
import app.invigilator.core.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class LinkingCompletionUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val navigateToParentHome: Boolean = false,
)

@HiltViewModel
class LinkingCompletionViewModel @Inject constructor(
    private val linkingRepository: LinkingRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val studentUid: String = checkNotNull(savedStateHandle["studentUid"]) {
        "LinkingCompletionViewModel requires 'studentUid' in SavedStateHandle"
    }
    private val studentDisplayName: String = savedStateHandle["studentDisplayName"] ?: ""

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
        val parentUid = authRepository.currentUserId ?: run {
            _uiState.update { it.copy(isLoading = false, error = "Not signed in. Please restart.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val linkedDoc = LinkedStudentDoc(
                studentUid = studentUid,
                studentDisplayName = studentDisplayName,
                linkType = "parent_minor",
                consentRecordId = "", // TODO Sprint 3: thread consent ID through nav args
            )

            val createResult = linkingRepository.createLinkedStudentRecord(parentUid, linkedDoc)
            if (createResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to link student. Please try again.",
                    )
                }
                return@launch
            }

            val activateResult = userRepository.activateAccount(studentUid, parentUid = parentUid)
            if (activateResult.isFailure) {
                // Step 2 failed — attempt rollback of step 1
                val rollbackResult = linkingRepository.deleteLinkedStudentRecord(parentUid, studentUid)
                if (rollbackResult.isFailure) {
                    Timber.e(
                        activateResult.exceptionOrNull(),
                        "CRITICAL: could not activate student $studentUid after linking for parent $parentUid; " +
                                "rollback also failed — linked student doc may be orphaned",
                    )
                }
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to activate student account. Please try again.")
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = false, navigateToParentHome = true) }
        }
    }
}
