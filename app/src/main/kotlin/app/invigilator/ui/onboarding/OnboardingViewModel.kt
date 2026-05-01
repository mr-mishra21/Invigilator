package app.invigilator.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.user.AccountStatus
import app.invigilator.core.user.UserDoc
import app.invigilator.core.user.UserRepository
import app.invigilator.core.user.UserRole
import app.invigilator.core.util.toUserMessage
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.util.Date
import javax.inject.Inject

sealed interface OnboardingDestination {
    /** Adult student → show AdultStudentSelfConsent (Phase 4) */
    data object ConsentAdultStudent : OnboardingDestination
    /** Minor student → show share-code screen (Phase 4) */
    data object StudentShareCode : OnboardingDestination
    /** Parent → show ParentTermsOfService (Phase 4) */
    data object ConsentParentToS : OnboardingDestination
}

data class OnboardingUiState(
    val role: String = "",
    val dobMillis: Long? = null,
    val uid: String? = null,
    val name: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val nameSubmitDone: OnboardingDestination? = null,
) {
    val age: Int?
        get() = dobMillis?.let { millis ->
            val dob = LocalDate.ofEpochDay(millis / 86_400_000L)
            Period.between(dob, LocalDate.now()).years
        }

    val isAdult: Boolean get() = (age ?: 0) >= 18
}

sealed interface OnboardingEvent {
    data class RoleSelected(val role: String) : OnboardingEvent
    data class DobSelected(val millis: Long) : OnboardingEvent
    data class UidReceived(val uid: String) : OnboardingEvent
    data class NameChanged(val name: String) : OnboardingEvent
    data object SubmitName : OnboardingEvent
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.RoleSelected  -> _uiState.update { it.copy(role = event.role, error = null) }
            is OnboardingEvent.DobSelected   -> _uiState.update { it.copy(dobMillis = event.millis, error = null) }
            is OnboardingEvent.UidReceived   -> _uiState.update { it.copy(uid = event.uid) }
            is OnboardingEvent.NameChanged   -> _uiState.update { it.copy(name = event.name, error = null) }
            OnboardingEvent.SubmitName       -> submitName()
        }
    }

    fun clearNameSubmitDone() {
        _uiState.update { it.copy(nameSubmitDone = null) }
    }

    private fun submitName() {
        val state = _uiState.value
        val uid   = state.uid ?: return
        val name  = state.name.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Please enter your name") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val doc = UserDoc(
                uid           = uid,
                role          = state.role,
                displayName   = name,
                phoneNumber   = "",        // already on Firebase Auth; not re-captured here
                dateOfBirth   = state.dobMillis?.let { Timestamp(Date(it)) },
                createdAt     = Timestamp.now(),
                accountStatus = AccountStatus.PENDING_CONSENT.firestoreValue,
            )

            userRepository.createUser(doc).fold(
                onSuccess = {
                    val destination = when {
                        state.role == UserRole.PARENT.firestoreValue ->
                            OnboardingDestination.ConsentParentToS

                        state.role == UserRole.STUDENT.firestoreValue && state.isAdult ->
                            OnboardingDestination.ConsentAdultStudent

                        else ->
                            OnboardingDestination.StudentShareCode
                    }
                    _uiState.update { it.copy(isLoading = false, nameSubmitDone = destination) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) }
                },
            )
        }
    }
}
