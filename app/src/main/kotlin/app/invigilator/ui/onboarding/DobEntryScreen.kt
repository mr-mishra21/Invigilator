package app.invigilator.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DobEntryRoute(
    role: String,
    onDobConfirmed: () -> Unit,
    onboardingViewModel: OnboardingViewModel,
) {
    val state by onboardingViewModel.uiState.collectAsStateWithLifecycle()

    DobEntryScreen(
        dobMillis = state.dobMillis,
        age = state.age,
        onDobSelected = { millis ->
            onboardingViewModel.onEvent(OnboardingEvent.DobSelected(millis))
        },
        onContinue = onDobConfirmed,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DobEntryScreen(
    dobMillis: Long?,
    age: Int?,
    onDobSelected: (Long) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dobMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDobSelected(it) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "What is your date of birth?",
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "We use this to determine whether parental consent is required.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (dobMillis != null) {
            val formatted = Instant.ofEpochMilli(dobMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(dateFormatter)

            Text(
                text = formatted,
                style = MaterialTheme.typography.titleLarge,
            )

            if (age != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Age: $age",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(if (dobMillis == null) "Select date of birth" else "Change date")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onContinue,
            enabled = dobMillis != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Continue")
        }
    }
}
