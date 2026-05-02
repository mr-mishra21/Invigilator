package app.invigilator.ui.linking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.linking.LinkingRepository
import app.invigilator.core.user.AccountStatus
import app.invigilator.core.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LinkingUiState(
    val code: String = "",
    val secondsRemaining: Int = 1800,    // 30 * 60
    val isClaimed: Boolean = false,
    val navigateToStudentHome: Boolean = false,
    val loggedOut: Boolean = false,
    val isGenerating: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LinkingViewModel @Inject constructor(
    private val linkingRepository: LinkingRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkingUiState())
    val uiState: StateFlow<LinkingUiState> = _uiState.asStateFlow()

    private var codeListenerJob: Job? = null
    private var statusListenerJob: Job? = null
    private var tickerJob: Job? = null

    init {
        val uid = authRepository.currentUserId
        if (uid != null) {
            startStatusListener(uid)
            generateCode()
        }
    }

    fun getNewCode() {
        generateCode()
    }

    fun clearNavigateToStudentHome() {
        _uiState.update { it.copy(navigateToStudentHome = false) }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.update { it.copy(loggedOut = true) }
        }
    }

    fun clearLoggedOut() {
        _uiState.update { it.copy(loggedOut = false) }
    }

    private fun generateCode() {
        val uid = authRepository.currentUserId ?: return
        codeListenerJob?.cancel()
        tickerJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            val result = linkingRepository.generateCode(uid)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to generate code.",
                    )
                }
                return@launch
            }
            val code = result.getOrThrow()
            _uiState.update {
                it.copy(code = code, secondsRemaining = 1800, isGenerating = false, isClaimed = false)
            }
            startCodeListener(code)
            startTicker()
        }
    }

    private fun startCodeListener(code: String) {
        codeListenerJob = viewModelScope.launch {
            linkingRepository.observeLinkingCode(code).collect { doc ->
                _uiState.update { it.copy(isClaimed = doc?.claimedBy != null) }
            }
        }
    }

    private fun startStatusListener(uid: String) {
        statusListenerJob = viewModelScope.launch {
            userRepository.observeUser(uid).collect { doc ->
                if (AccountStatus.fromFirestore(doc?.accountStatus ?: "") == AccountStatus.ACTIVE) {
                    _uiState.update { it.copy(navigateToStudentHome = true) }
                }
            }
        }
    }

    private fun startTicker() {
        tickerJob = viewModelScope.launch {
            while (isActive && _uiState.value.secondsRemaining > 0) {
                delay(1_000)
                _uiState.update { it.copy(secondsRemaining = (it.secondsRemaining - 1).coerceAtLeast(0)) }
            }
        }
    }
}
