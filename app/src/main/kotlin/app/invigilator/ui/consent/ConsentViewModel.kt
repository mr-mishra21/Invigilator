package app.invigilator.ui.consent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.consent.ConsentDoc
import app.invigilator.core.consent.ConsentRepository
import app.invigilator.core.consent.ConsentType
import app.invigilator.core.consent.ConsentVersions
import app.invigilator.core.user.UserRepository
import app.invigilator.core.util.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject

sealed interface ConsentEvent {
    data class SignatureChanged(val text: String) : ConsentEvent
    data class LanguageChanged(val lang: String) : ConsentEvent
    data object ScrolledToEnd : ConsentEvent
    data object AgreedTapped : ConsentEvent
    data object ErrorDismissed : ConsentEvent
}

data class ConsentUiState(
    val consentText: String = "",
    val consentType: String = "",
    val consentVersion: String = "",
    val language: String = "en",
    val signature: String = "",
    val hasScrolledToEnd: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** true → the Route composable should navigate away */
    val isComplete: Boolean = false,
    /** Set when isComplete = true; passed to onComplete so callers can forward the consent ID. */
    val consentId: String? = null,
) {
    val canAgree: Boolean get() = hasScrolledToEnd && signature.isNotBlank() && !isLoading
}

@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val consentRepository: ConsentRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Read directly from SavedStateHandle (avoids android.os.Bundle usage in unit tests).
    private val consentTypeStr: String = checkNotNull(savedStateHandle["type"]) {
        "ConsentViewModel requires 'type' in SavedStateHandle (nav arg from Route.Consent)"
    }
    private val consentType: ConsentType = checkNotNull(ConsentType.fromFirestore(consentTypeStr)) {
        "Unknown consent type: $consentTypeStr"
    }

    private val _uiState = MutableStateFlow(
        ConsentUiState(
            consentType = consentTypeStr,
            consentVersion = ConsentVersions.currentVersionFor(consentType),
            language = resolveLanguage(),
        )
    )
    val uiState: StateFlow<ConsentUiState> = _uiState.asStateFlow()

    init {
        loadConsentText()
    }

    fun onEvent(event: ConsentEvent) {
        when (event) {
            is ConsentEvent.SignatureChanged -> _uiState.update {
                it.copy(signature = event.text, error = null)
            }
            is ConsentEvent.LanguageChanged -> {
                _uiState.update { it.copy(language = event.lang) }
                loadConsentText()
            }
            ConsentEvent.ScrolledToEnd -> _uiState.update { it.copy(hasScrolledToEnd = true) }
            ConsentEvent.AgreedTapped -> submitConsent()
            ConsentEvent.ErrorDismissed -> _uiState.update { it.copy(error = null) }
        }
    }

    fun clearComplete() {
        _uiState.update { it.copy(isComplete = false, consentId = null) }
    }

    private fun loadConsentText() {
        val state = _uiState.value
        val result = consentRepository.getConsentText(consentType, state.language)
        if (result != null) {
            _uiState.update { it.copy(consentText = result.text) }
        }
    }

    private fun submitConsent() {
        val state = _uiState.value
        if (!state.canAgree) return
        val uid = authRepository.currentUserId ?: run {
            _uiState.update { it.copy(error = "Not signed in. Please restart the app.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val textResult = consentRepository.getConsentText(consentType, state.language)
                ?: run {
                    _uiState.update { it.copy(isLoading = false, error = "Could not load consent text. Try again.") }
                    return@launch
                }

            val doc = ConsentDoc(
                consentType = consentTypeStr,
                consentVersion = state.consentVersion,
                consentTextHash = textResult.hash,
                consentLanguage = state.language,
                consenterUid = uid,
                consenterRole = "",        // resolved server-side from user doc
                subjectUid = uid,          // self-consent; parent-for-minor overrides in Part 4
                signatureText = state.signature.trim(),
                deviceFingerprint = "",    // TODO Sprint 3: populate androidId-derived hash
            )

            val recordResult = consentRepository.recordConsent(doc)
            if (recordResult.isFailure) {
                _uiState.update {
                    it.copy(isLoading = false, error = recordResult.exceptionOrNull()?.toUserMessage())
                }
                return@launch
            }

            val consentId = recordResult.getOrThrow()

            // Race the Firestore listener against a 30-second timeout
            val serverConfirmed = withTimeoutOrNull(30_000) {
                consentRepository.awaitServerTimestamp(consentId).first { it }
            } != null

            if (serverConfirmed) {
                // ADULT_STUDENT_SELF and PARENT_TOS activate the consenter's own account.
                // PARENT_FOR_MINOR activation is a separate batch write handled by Part 4.
                val needsSelfActivation = consentType == ConsentType.ADULT_STUDENT_SELF ||
                        consentType == ConsentType.PARENT_TERMS_OF_SERVICE
                if (needsSelfActivation) {
                    val activateResult = userRepository.activateAccount(uid)
                    if (activateResult.isFailure) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = activateResult.exceptionOrNull()?.toUserMessage()
                                    ?: "Failed to activate account. Try again.",
                            )
                        }
                        return@launch
                    }
                }
                _uiState.update { it.copy(isLoading = false, isComplete = true, consentId = consentId) }
            } else {
                // Timeout — delete the incomplete doc so the user can retry
                consentRepository.withdrawConsent(consentId, uid)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Something went wrong confirming your consent. Try again.",
                    )
                }
            }
        }
    }

    private fun resolveLanguage(): String {
        val tag = Locale.getDefault().language
        return if (tag in SUPPORTED_LANGUAGES) tag else "en"
    }

    companion object {
        val SUPPORTED_LANGUAGES = setOf("en", "as", "hi", "bn")
    }
}
