package app.invigilator.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.util.PhoneFormatter
import app.invigilator.core.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhoneEntryUiState(
    val phone: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Non-null once OTP has been sent; triggers nav to OtpEntry. */
    val normalizedPhone: String? = null,
)

sealed interface PhoneEntryEvent {
    data class PhoneChanged(val phone: String) : PhoneEntryEvent
    data object Submit : PhoneEntryEvent
}

@HiltViewModel
class PhoneEntryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneEntryUiState())
    val uiState: StateFlow<PhoneEntryUiState> = _uiState.asStateFlow()

    fun onEvent(event: PhoneEntryEvent) {
        when (event) {
            is PhoneEntryEvent.PhoneChanged -> _uiState.update { it.copy(phone = event.phone, error = null) }
            PhoneEntryEvent.Submit          -> sendOtp()
        }
    }

    fun clearNavigationFlag() {
        _uiState.update { it.copy(normalizedPhone = null) }
    }

    private fun sendOtp() {
        val normalized = PhoneFormatter.normalize(_uiState.value.phone).getOrElse {
            _uiState.update { it.copy(error = "Enter a valid 10-digit Indian phone number") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.sendOtp(normalized)
                .onSuccess { _uiState.update { it.copy(isLoading = false, normalizedPhone = normalized) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }
}
