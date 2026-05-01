package app.invigilator.ui.linking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.invigilator.core.linking.ClaimResult

@Composable
fun EnterCodeRoute(
    onStudentConfirmed: (ClaimResult) -> Unit,
    viewModel: EnterCodeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.claimedResult) {
        val result = state.claimedResult
        if (result != null) {
            viewModel.clearClaimedResult()
            onStudentConfirmed(result)
        }
    }

    EnterCodeScreen(
        state = state,
        onDigitChanged = viewModel::onDigitChanged,
        onAllDigitsEntered = viewModel::onAllDigitsEntered,
        onConfirmTapped = viewModel::onConfirmTapped,
        modifier = modifier,
    )
}

@Composable
fun EnterCodeScreen(
    state: EnterCodeUiState,
    onDigitChanged: (Int, String) -> Unit,
    onAllDigitsEntered: (String) -> Unit,
    onConfirmTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember { List(6) { FocusRequester() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Enter your student's code", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ask your student to open the Invigilator app and share their 6-digit code.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.digits.forEachIndexed { index, digit ->
                OutlinedTextField(
                    value = digit,
                    onValueChange = { newVal ->
                        val cleaned = newVal.filter { it.isDigit() }
                        if (cleaned.length >= 6) {
                            // Paste detected — fill all boxes
                            onAllDigitsEntered(cleaned)
                            focusRequesters.last().requestFocus()
                        } else {
                            val single = cleaned.take(1)
                            onDigitChanged(index, single)
                            if (single.isNotEmpty() && index < 5) {
                                focusRequesters[index + 1].requestFocus()
                            }
                        }
                    },
                    modifier = Modifier
                        .width(44.dp)
                        .focusRequester(focusRequesters[index]),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onConfirmTapped,
            enabled = state.isConfirmEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Confirm")
            }
        }
    }
}
