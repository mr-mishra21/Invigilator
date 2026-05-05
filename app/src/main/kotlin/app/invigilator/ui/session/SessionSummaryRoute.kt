package app.invigilator.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SessionSummaryRoute(
    sessionId: String,
    studentUid: String,
    onStartAnother: () -> Unit,
    onDone: () -> Unit,
    viewModel: SessionSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SessionSummaryScreen(
        state = state,
        onStartAnother = onStartAnother,
        onDone = onDone,
    )
}
