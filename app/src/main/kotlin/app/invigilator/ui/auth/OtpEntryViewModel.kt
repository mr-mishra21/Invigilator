package app.invigilator.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.user.AccountStatus
import app.invigilator.core.user.UserDoc
import app.invigilator.core.user.UserRepository
import app.invigilator.core.user.UserRole
import app.invigilator.core.util.toUserMessage
import app.invigilator.ui.nav.AuthFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OtpDestination {
    data object ProceedToCreateUser : OtpDestination
    data class GoToHome(val role: UserRole) : OtpDestination
    data class ResumeConsent(val userDoc: UserDoc) : OtpDestination
    data object UnknownNumber : OtpDestination
}

data class OtpEntryUiState(
    val otp: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val nextDestination: OtpDestination? = null,
    val resendSecondsRemaining: Int = 60,
    val isResending: Boolean = false,
)

sealed interface OtpEntryEvent {
    data class OtpChanged(val otp: String) : OtpEntryEvent
    data object Submit : OtpEntryEvent
    data object ResendOtp : OtpEntryEvent
}

@HiltViewModel
class OtpEntryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val phone: String = checkNotNull(savedStateHandle["phoneE164"])
    private val flow: AuthFlow = savedStateHandle.get<AuthFlow>("flow") ?: AuthFlow.NEW_USER

    private val _uiState = MutableStateFlow(OtpEntryUiState())
    val uiState: StateFlow<OtpEntryUiState> = _uiState.asStateFlow()

    init {
        startResendTimer()
    }

    fun onEvent(event: OtpEntryEvent) {
        when (event) {
            is OtpEntryEvent.OtpChanged -> _uiState.update { it.copy(otp = event.otp, error = null) }
            OtpEntryEvent.Submit        -> verifyOtp()
            OtpEntryEvent.ResendOtp     -> resendOtp()
        }
    }

    private fun verifyOtp() {
        val otp = _uiState.value.otp
        if (otp.length != 6) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.verifyOtp(otp)
                .onSuccess { uid -> handleOtpSuccess(uid) }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.toUserMessage(), otp = "") }
                }
        }
    }

    private suspend fun handleOtpSuccess(uid: String) {
        when (flow) {
            AuthFlow.NEW_USER -> {
                _uiState.update { it.copy(isLoading = false, nextDestination = OtpDestination.ProceedToCreateUser) }
            }
            AuthFlow.SIGN_IN -> {
                userRepository.getUser(uid)
                    .onSuccess { userDoc ->
                        when {
                            userDoc == null -> {
                                authRepository.signOut()
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        nextDestination = OtpDestination.UnknownNumber,
                                        error = "We don't recognize this number. Try creating an account instead.",
                                    )
                                }
                            }
                            userDoc.accountStatus == AccountStatus.ACTIVE.firestoreValue -> {
                                val role = UserRole.fromFirestore(userDoc.role)
                                if (role != null) {
                                    _uiState.update {
                                        it.copy(isLoading = false, nextDestination = OtpDestination.GoToHome(role))
                                    }
                                } else {
                                    _uiState.update {
                                        it.copy(isLoading = false, nextDestination = OtpDestination.ResumeConsent(userDoc))
                                    }
                                }
                            }
                            else -> {
                                _uiState.update {
                                    it.copy(isLoading = false, nextDestination = OtpDestination.ResumeConsent(userDoc))
                                }
                            }
                        }
                    }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(isLoading = false, error = "Could not load your account. Please try again.")
                        }
                    }
            }
        }
    }

    private fun resendOtp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isResending = true, error = null) }
            authRepository.sendOtp(phone)
                .onSuccess {
                    _uiState.update { it.copy(isResending = false, resendSecondsRemaining = RESEND_TIMEOUT_SECONDS) }
                    startResendTimer()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isResending = false, error = e.toUserMessage()) }
                }
        }
    }

    private fun startResendTimer() {
        viewModelScope.launch {
            for (remaining in RESEND_TIMEOUT_SECONDS downTo 0) {
                _uiState.update { it.copy(resendSecondsRemaining = remaining) }
                if (remaining > 0) delay(1_000L)
            }
        }
    }

    companion object {
        const val RESEND_TIMEOUT_SECONDS = 60
    }
}
