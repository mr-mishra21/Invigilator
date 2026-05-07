package app.invigilator.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudentHomeUiState(
    val displayName: String = "",
    val parentDisplayName: String? = null,
)

@HiltViewModel
class StudentHomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StudentHomeUiState())
    val uiState: StateFlow<StudentHomeUiState> = _uiState.asStateFlow()

    init {
        loadStudentData()
    }

    private fun loadStudentData() {
        val uid = authRepository.currentUserId ?: return
        viewModelScope.launch {
            userRepository.getUser(uid).onSuccess { doc ->
                _uiState.update { it.copy(displayName = doc?.displayName ?: "") }
                val parentUid = doc?.parentUid ?: return@onSuccess
                userRepository.getUser(parentUid).onSuccess { parentDoc ->
                    _uiState.update { it.copy(parentDisplayName = parentDoc?.displayName) }
                }
            }
        }
    }
}
