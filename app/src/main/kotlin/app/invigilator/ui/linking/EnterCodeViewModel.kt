package app.invigilator.ui.linking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.linking.ClaimResult
import app.invigilator.core.linking.LinkingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EnterCodeUiState(
    val digits: List<String> = List(6) { "" },
    val isLoading: Boolean = false,
    val error: String? = null,
    val claimedResult: ClaimResult? = null,
) {
    val isConfirmEnabled: Boolean get() = digits.all { it.isNotBlank() } && !isLoading
    val code: String get() = digits.joinToString("")
}

@HiltViewModel
class EnterCodeViewModel @Inject constructor(
    private val linkingRepository: LinkingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnterCodeUiState())
    val uiState: StateFlow<EnterCodeUiState> = _uiState.asStateFlow()

    fun onDigitChanged(index: Int, digit: String) {
        val newDigits = _uiState.value.digits.toMutableList()
        newDigits[index] = digit.filter { it.isDigit() }.take(1)
        _uiState.update { it.copy(digits = newDigits.toList(), error = null) }
    }

    fun onAllDigitsEntered(digits: String) {
        val cleaned = digits.filter { it.isDigit() }.take(6)
        if (cleaned.length < 6) return
        _uiState.update { it.copy(digits = cleaned.map { c -> c.toString() }, error = null) }
    }

    fun onConfirmTapped() {
        val code = _uiState.value.code
        if (code.length < 6) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = linkingRepository.claimCode(code)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, claimedResult = result.getOrThrow()) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message
                            ?: "Code not found. Check the digits with your student.",
                    )
                }
            }
        }
    }

    fun clearClaimedResult() {
        _uiState.update { it.copy(claimedResult = null) }
    }
}
