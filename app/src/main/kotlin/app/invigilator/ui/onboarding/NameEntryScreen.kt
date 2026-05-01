package app.invigilator.ui.onboarding

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun NameEntryRoute(
    onAdultStudentConsent: () -> Unit,
    onMinorStudentShareCode: () -> Unit,
    onParentConsent: () -> Unit,
    onboardingViewModel: OnboardingViewModel,
) {
    val state by onboardingViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.nameSubmitDone) {
        when (state.nameSubmitDone) {
            OnboardingDestination.ConsentAdultStudent -> {
                onboardingViewModel.clearNameSubmitDone()
                onAdultStudentConsent()
            }
            OnboardingDestination.StudentShareCode -> {
                onboardingViewModel.clearNameSubmitDone()
                onMinorStudentShareCode()
            }
            OnboardingDestination.ConsentParentToS -> {
                onboardingViewModel.clearNameSubmitDone()
                onParentConsent()
            }
            null -> Unit
        }
    }

    NameEntryScreen(
        name = state.name,
        isLoading = state.isLoading,
        error = state.error,
        onEvent = onboardingViewModel::onEvent,
    )
}

@Composable
fun NameEntryScreen(
    name: String,
    isLoading: Boolean,
    error: String?,
    onEvent: (OnboardingEvent) -> Unit,
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
            text = "What's your name?",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This is how you'll appear in the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { onEvent(OnboardingEvent.NameChanged(it)) },
            label = { Text("Full name") },
            isError = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onEvent(OnboardingEvent.SubmitName) },
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onEvent(OnboardingEvent.SubmitName) },
            enabled = !isLoading && name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Continue")
            }
        }
    }
}
