package app.invigilator.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.session.SessionSummaryRepository
import app.invigilator.core.util.AppNameResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SessionSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    summaryRepo: SessionSummaryRepository,
    private val appNameResolver: AppNameResolver,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>("sessionId") ?: ""
    private val studentUid: String = savedStateHandle.get<String>("studentUid") ?: ""

    val state: StateFlow<SessionSummaryUiState> = summaryRepo
        .observeRecentSessions(studentUid, limit = 10)
        .map { sessions ->
            val match = sessions.firstOrNull { it.sessionId == sessionId }
            if (match == null) {
                SessionSummaryUiState(loading = true)
            } else {
                SessionSummaryUiState(
                    loading = false,
                    durationMinutes = (match.durationSeconds / 60).toInt(),
                    distractionCount = match.distractionCount,
                    verdict = computeVerdict(match.distractionCount),
                    breakdown = match.distractions.map { d ->
                        BreakdownRow(
                            displayName = appNameResolver.resolveDisplayName(d.packageName),
                            dwellSeconds = d.dwellSeconds,
                        )
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionSummaryUiState(loading = true))

    private fun computeVerdict(count: Int): Verdict = when {
        count == 0 -> Verdict.EXCELLENT
        count in 1..2 -> Verdict.GOOD
        count in 3..5 -> Verdict.SOME
        else -> Verdict.LOTS
    }
}

data class SessionSummaryUiState(
    val loading: Boolean = true,
    val durationMinutes: Int = 0,
    val distractionCount: Int = 0,
    val verdict: Verdict = Verdict.EXCELLENT,
    val breakdown: List<BreakdownRow> = emptyList(),
)

data class BreakdownRow(
    val displayName: String,
    val dwellSeconds: Long,
)

enum class Verdict { EXCELLENT, GOOD, SOME, LOTS }
