package app.invigilator.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.invigilator.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSummaryScreen(
    state: SessionSummaryUiState,
    onStartAnother: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.session_summary_title)) })
        },
    ) { innerPadding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.session_summary_x_minute_session, state.durationMinutes),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            item {
                val (verdictText, verdictColor) = when (state.verdict) {
                    Verdict.EXCELLENT -> stringResource(R.string.verdict_excellent) to Color(0xFF2E7D32)
                    Verdict.GOOD -> stringResource(R.string.verdict_good) to Color(0xFF1565C0)
                    Verdict.SOME -> stringResource(R.string.verdict_some) to Color(0xFFE65100)
                    Verdict.LOTS -> stringResource(R.string.verdict_lots) to Color(0xFFC62828)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = verdictColor.copy(alpha = 0.1f),
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = verdictText,
                            style = MaterialTheme.typography.headlineMedium,
                            color = verdictColor,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.session_summary_distractions_header),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
            }

            if (state.breakdown.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.session_summary_no_distractions),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.breakdown) { row ->
                    Text(
                        text = stringResource(
                            R.string.session_summary_dwell_format,
                            row.displayName,
                            row.dwellSeconds,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onStartAnother,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_start_another))
                    }
                    TextButton(onClick = onDone) {
                        Text(stringResource(R.string.action_done))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
