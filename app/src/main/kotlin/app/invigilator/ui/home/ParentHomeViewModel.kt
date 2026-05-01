package app.invigilator.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.linking.LinkingRepository
import app.invigilator.core.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LinkedStudentItem(
    val uid: String,
    val displayName: String,
    val accountStatus: String,
)

data class ParentHomeUiState(
    val linkedStudents: List<LinkedStudentItem> = emptyList(),
    val isLoading: Boolean = true,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class ParentHomeViewModel @Inject constructor(
    private val linkingRepository: LinkingRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParentHomeUiState())
    val uiState: StateFlow<ParentHomeUiState> = _uiState.asStateFlow()

    init {
        observeLinkedStudents()
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.update { it.copy(loggedOut = true) }
        }
    }

    private fun observeLinkedStudents() {
        val parentUid = authRepository.currentUserId ?: run {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            linkingRepository.observeLinkedStudents(parentUid).collect { docs ->
                val students = docs.map { doc ->
                    val status = userRepository.getUser(doc.studentUid)
                        .getOrNull()?.accountStatus ?: "pending_consent"
                    LinkedStudentItem(
                        uid = doc.studentUid,
                        displayName = doc.studentDisplayName,
                        accountStatus = status,
                    )
                }
                _uiState.update { it.copy(linkedStudents = students, isLoading = false) }
            }
        }
    }
}
