package app.invigilator.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.invigilator.core.session.SessionType

@Composable
fun StartSessionRoute(
    onStart: (SessionType) -> Unit,
    onBack: () -> Unit,
    viewModel: StartSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    StartSessionScreen(
        state = state,
        onEvent = { event ->
            if (event is StartSessionEvent.StartClicked) {
                onStart(viewModel.buildSessionType())
            } else {
                viewModel.onEvent(event)
            }
        },
        onBack = onBack,
        onTestVoice = { viewModel.testVoice() },
    )
}
