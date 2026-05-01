package app.invigilator.ui.linking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun StudentShareCodeRoute(
    onNavigateToStudentHome: () -> Unit,
    viewModel: LinkingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.navigateToStudentHome) {
        if (state.navigateToStudentHome) {
            viewModel.clearNavigateToStudentHome()
            onNavigateToStudentHome()
        }
    }

    StudentShareCodeScreen(
        state = state,
        onGetNewCode = viewModel::getNewCode,
        modifier = modifier,
    )
}

@Composable
fun StudentShareCodeScreen(
    state: LinkingUiState,
    onGetNewCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Share this code with your parent.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "They need to enter it in the Invigilator app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            if (state.isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            } else if (state.code.isNotBlank()) {
                // Display code with wide spacing: "4  7  2  9  1  3"
                Text(
                    text = state.code.toCharArray().joinToString("  "),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))

            val minutes = state.secondsRemaining / 60
            val seconds = state.secondsRemaining % 60
            Text(
                text = "Expires in %02d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(32.dp))

            OutlinedButton(
                onClick = onGetNewCode,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isGenerating,
            ) {
                Text("Get a new code")
            }
        }

        // Overlay banner when parent has claimed the code
        if (state.isClaimed) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Your parent is reviewing your consent…",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
