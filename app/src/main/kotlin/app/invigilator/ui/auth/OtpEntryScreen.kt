package app.invigilator.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OtpEntryRoute(
    role: String,
    phone: String,
    onVerified: (uid: String) -> Unit,
    onWrongNumber: () -> Unit,
    viewModel: OtpEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.verifiedUid) {
        state.verifiedUid?.let { uid ->
            viewModel.clearNavigationFlag()
            onVerified(uid)
        }
    }

    OtpEntryScreen(
        state = state,
        phone = phone,
        onEvent = viewModel::onEvent,
        onWrongNumber = onWrongNumber,
    )
}

@Composable
fun OtpEntryScreen(
    state: OtpEntryUiState,
    phone: String,
    onEvent: (OtpEntryEvent) -> Unit,
    onWrongNumber: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Enter the code",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sent to $phone",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = state.otp,
            onValueChange = { if (it.length <= 6) onEvent(OtpEntryEvent.OtpChanged(it)) },
            label = { Text("6-digit code") },
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onEvent(OtpEntryEvent.Submit) },
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onEvent(OtpEntryEvent.Submit) },
            enabled = !state.isLoading && state.otp.length == 6,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Verify")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (state.resendSecondsRemaining > 0) {
            Text(
                text = "Resend code in ${state.resendSecondsRemaining}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TextButton(
                onClick = { onEvent(OtpEntryEvent.ResendOtp) },
                enabled = !state.isResending,
            ) {
                Text(if (state.isResending) "Sending…" else "Resend code")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onWrongNumber) {
            Text("Wrong number?")
        }
    }
}
