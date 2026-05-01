package app.invigilator.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.auth.AuthState
import app.invigilator.core.user.AccountStatus
import app.invigilator.core.user.UserDoc
import app.invigilator.core.user.UserRepository
import app.invigilator.core.user.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject

sealed interface SplashDestination {
    data object Onboarding : SplashDestination
    data object ParentHome : SplashDestination
    data object StudentHome : SplashDestination
    /** Pending-consent adult student → resume at AdultStudentSelfConsent screen. */
    data object AdultStudentConsent : SplashDestination
    /** Pending-consent minor student → resume at StudentShareCode screen. */
    data object StudentShareCodeResume : SplashDestination
    /** Pending-consent parent → resume at ParentTermsOfService consent screen. */
    data object ParentConsentResume : SplashDestination
}

data class SplashUiState(
    val destination: SplashDestination? = null,
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val authState = authRepository.authState
                .filter { it !is AuthState.Loading }
                .first()

            val destination = when (authState) {
                is AuthState.SignedIn -> resolveSignedInDestination(authState.uid)
                is AuthState.SignedOut -> SplashDestination.Onboarding
                is AuthState.Loading -> SplashDestination.Onboarding
            }
            _uiState.update { it.copy(destination = destination) }
        }
    }

    private suspend fun resolveSignedInDestination(uid: String): SplashDestination {
        return userRepository.getUser(uid).fold(
            onSuccess = { doc -> routeForDoc(doc) },
            onFailure = { SplashDestination.Onboarding },
        )
    }

    private fun routeForDoc(doc: UserDoc?): SplashDestination {
        if (doc == null) return SplashDestination.Onboarding

        val status = AccountStatus.fromFirestore(doc.accountStatus)
            ?: return SplashDestination.Onboarding

        return when (status) {
            AccountStatus.ACTIVE -> when (doc.role) {
                UserRole.PARENT.firestoreValue -> SplashDestination.ParentHome
                UserRole.STUDENT.firestoreValue -> SplashDestination.StudentHome
                else -> SplashDestination.Onboarding
            }
            AccountStatus.PENDING_CONSENT -> when (doc.role) {
                UserRole.PARENT.firestoreValue -> SplashDestination.ParentConsentResume
                UserRole.STUDENT.firestoreValue -> {
                    val age = computeAge(doc)
                    if (age != null && age >= 18) SplashDestination.AdultStudentConsent
                    else SplashDestination.StudentShareCodeResume
                }
                else -> SplashDestination.Onboarding
            }
            AccountStatus.SUSPENDED -> SplashDestination.Onboarding
        }
    }

    private fun computeAge(doc: UserDoc): Int? {
        val dob = doc.dateOfBirth ?: return null
        val dobLocal = dob.toDate().toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return Period.between(dobLocal, LocalDate.now()).years
    }
}
