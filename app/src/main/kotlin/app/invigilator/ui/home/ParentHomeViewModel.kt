package app.invigilator.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.linking.LinkingRepository
import app.invigilator.core.session.SessionDoc
import app.invigilator.core.session.SessionSummaryRepository
import app.invigilator.core.user.LinkedStudentDoc
import app.invigilator.core.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class LinkedStudentRow(
    val studentUid: String,
    val displayName: String,
    val accountStatus: String,
    val sessionsToday: Int,
    val lastSessionMinutesAgo: Long?,  // null if no sessions yet
)

data class ParentHomeUiState(
    val linkedStudents: List<LinkedStudentRow> = emptyList(),
    val isLoading: Boolean = true,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class ParentHomeViewModel @Inject constructor(
    private val linkingRepository: LinkingRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val sessionSummaryRepo: SessionSummaryRepository,
) : ViewModel() {

    private val _loggedOut = MutableStateFlow(false)

    val uiState: StateFlow<ParentHomeUiState> = combine(
        buildStudentRowFlow(),
        _loggedOut,
    ) { baseState, loggedOut ->
        baseState.copy(loggedOut = loggedOut)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ParentHomeUiState())

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _loggedOut.value = true
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun buildStudentRowFlow(): kotlinx.coroutines.flow.Flow<ParentHomeUiState> {
        val parentUid = authRepository.currentUserId
            ?: return flowOf(ParentHomeUiState(isLoading = false))

        return linkingRepository.observeLinkedStudents(parentUid)
            .flatMapLatest { docs ->
                if (docs.isEmpty()) {
                    flowOf(ParentHomeUiState(isLoading = false))
                } else {
                    val rowFlows = docs.map { doc -> rowFlowFor(doc) }
                    combine(rowFlows) { rows ->
                        ParentHomeUiState(linkedStudents = rows.toList(), isLoading = false)
                    }
                }
            }
    }

    private fun rowFlowFor(doc: LinkedStudentDoc) =
        sessionSummaryRepo.observeRecentSessions(doc.studentUid, limit = 10)
            .map { sessions ->
                val accountStatus = userRepository.getUser(doc.studentUid)
                    .getOrNull()?.accountStatus ?: "pending_consent"
                buildRow(doc, accountStatus, sessions)
            }

    private fun buildRow(
        doc: LinkedStudentDoc,
        accountStatus: String,
        sessions: List<SessionDoc>,
    ): LinkedStudentRow {
        val today = LocalDate.now()
        val todaySessions = sessions.filter { session ->
            val sessionDate = session.endedAt.toDate()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            sessionDate == today
        }
        val lastSessionMinutesAgo = sessions.firstOrNull()?.let { s ->
            (System.currentTimeMillis() - s.endedAt.toDate().time) / 60_000
        }
        return LinkedStudentRow(
            studentUid = doc.studentUid,
            displayName = doc.studentDisplayName,
            accountStatus = accountStatus,
            sessionsToday = todaySessions.size,
            lastSessionMinutesAgo = lastSessionMinutesAgo,
        )
    }
}
