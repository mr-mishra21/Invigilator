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

@Composable
fun SessionActiveRoute(
    onSessionEnded: (sessionId: String, studentUid: String) -> Unit,
    viewModel: SessionActiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sessionContext by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(state.isActive) {
        // Capture context the moment the session is active
        val active = viewModel.activeSessionSnapshot()
        if (active != null && sessionContext == null) {
            sessionContext = active.sessionId to active.studentUid
        }
        // When session becomes inactive, navigate
        if (!state.isActive && sessionContext != null) {
            val (sid, uid) = sessionContext!!
            onSessionEnded(sid, uid)
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
