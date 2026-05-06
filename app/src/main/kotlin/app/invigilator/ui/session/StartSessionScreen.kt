package app.invigilator.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.invigilator.BuildConfig
import app.invigilator.R

private val DURATION_OPTIONS = listOf(15, 25, 45, 60, 90)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StartSessionScreen(
    state: StartSessionUiState,
    onEvent: (StartSessionEvent) -> Unit,
    onBack: () -> Unit,
    onTestVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.start_session_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Session type",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.mode == SessionMode.TIMED,
                    onClick = { onEvent(StartSessionEvent.ModeChanged(SessionMode.TIMED)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text(stringResource(R.string.start_session_timed))
                }
                SegmentedButton(
                    selected = state.mode == SessionMode.OPEN,
                    onClick = { onEvent(StartSessionEvent.ModeChanged(SessionMode.OPEN)) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text(stringResource(R.string.start_session_open))
                }
            }

            if (state.mode == SessionMode.TIMED) {
                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DURATION_OPTIONS.forEach { minutes ->
                        FilterChip(
                            selected = state.selectedDurationMinutes == minutes,
                            onClick = { onEvent(StartSessionEvent.DurationChanged(minutes)) },
                            label = { Text(durationLabel(minutes)) },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { onEvent(StartSessionEvent.StartClicked) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(stringResource(R.string.action_start))
            }

            Spacer(Modifier.height(16.dp))

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { onTestVoice() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.debug_test_voice_button))
                }
            }
        }
    }
}

@Composable
private fun durationLabel(minutes: Int): String = when (minutes) {
    15 -> stringResource(R.string.duration_15)
    25 -> stringResource(R.string.duration_25)
    45 -> stringResource(R.string.duration_45)
    60 -> stringResource(R.string.duration_60)
    90 -> stringResource(R.string.duration_90)
    else -> "$minutes min"
}
