package app.invigilator.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OtpEntryUiState(
    val otp: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Non-null once OTP verified; the caller stores uid in OnboardingViewModel then navigates. */
    val verifiedUid: String? = null,
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
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val phone: String = checkNotNull(savedStateHandle["phone"])

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

    fun clearNavigationFlag() {
        _uiState.update { it.copy(verifiedUid = null) }
    }

    private fun verifyOtp() {
        val otp = _uiState.value.otp
        if (otp.length != 6) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.verifyOtp(otp)
                .onSuccess { uid -> _uiState.update { it.copy(isLoading = false, verifiedUid = uid) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.toUserMessage(), otp = "") }
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
