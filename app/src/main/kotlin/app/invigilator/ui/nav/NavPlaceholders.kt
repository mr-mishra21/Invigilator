package app.invigilator.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Placeholder for ConsentScreen — replaced in Part 1. */
@Composable
internal fun ConsentPlaceholder(
    type: String,
    onNavigateToStudentHome: () -> Unit,
    onNavigateToParentHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Consent: $type",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Full consent screen coming in Part 1.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNavigateToStudentHome) { Text("→ Student home (temp)") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onNavigateToParentHome) { Text("→ Parent home (temp)") }
    }
}

/** Placeholder for StudentShareCodeScreen — replaced in Part 3. */
@Composable
internal fun ShareCodePlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Student Share Code",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Share-code screen coming in Part 3.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Placeholder for EnterCodeScreen — replaced in Part 4. */
@Composable
internal fun EnterCodePlaceholder(
    onNavigateToParentHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Enter Student Code",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Code-entry screen coming in Part 4.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNavigateToParentHome) { Text("← Back to Parent home") }
    }
}

/** Placeholder for ConfirmStudentScreen — replaced in Part 4. */
@Composable
internal fun ConfirmStudentPlaceholder(
    studentName: String,
    studentDobMillis: Long,
    studentUid: String,
    onConfirmed: () -> Unit,
    onNotMyChild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Confirm student: $studentName",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "UID: $studentUid  DOB: $studentDobMillis",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onConfirmed) { Text("Yes, continue") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onNotMyChild) { Text("Not my child") }
    }
}
