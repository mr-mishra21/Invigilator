package app.invigilator.ui.session

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.invigilator.blocker.SessionMonitorService

@Composable
fun SessionActiveRoute(
    onSessionEnded: () -> Unit,
    viewModel: SessionActiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.isActive) {
        if (!state.isActive) onSessionEnded()
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
