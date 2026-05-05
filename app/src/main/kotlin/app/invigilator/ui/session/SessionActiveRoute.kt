package app.invigilator.ui.session

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.invigilator.blocker.SessionMonitorService
import app.invigilator.core.session.SessionEndReason

private data class SessionContext(
    val sessionId: String,
    val studentUid: String,
    val plannedDurationMinutes: Int,
)

@Composable
fun SessionActiveRoute(
    onSessionEnded: (sessionId: String, studentUid: String) -> Unit,
    onTimerExpired: (sessionId: String, studentUid: String, durationMinutes: Int) -> Unit,
    viewModel: SessionActiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sessionContext by remember { mutableStateOf<SessionContext?>(null) }

    LaunchedEffect(state.isActive) {
        // Capture context the moment the session is active
        val active = viewModel.activeSessionSnapshot()
        if (active != null && sessionContext == null) {
            sessionContext = SessionContext(
                sessionId = active.sessionId,
                studentUid = active.studentUid,
                plannedDurationMinutes = active.plannedDurationMinutes,
            )
        }
        // When session becomes inactive, navigate
        if (!state.isActive && sessionContext != null) {
            val ctx = sessionContext!!
            val reason = viewModel.lastEndReasonSnapshot()
            if (reason == SessionEndReason.TIMER_EXPIRED) {
                onTimerExpired(ctx.sessionId, ctx.studentUid, ctx.plannedDurationMinutes)
            } else {
                onSessionEnded(ctx.sessionId, ctx.studentUid)
            }
        }
    }

    SessionActiveScreen(
        state = state,
        onEnd = {
            val intent = Intent(context, SessionMonitorService::class.java).apply {
                action = SessionMonitorService.ACTION_STOP
            }
            context.startService(intent)
        },
    )

    BackHandler(enabled = state.isActive) { /* swallow back press during session */ }
}
