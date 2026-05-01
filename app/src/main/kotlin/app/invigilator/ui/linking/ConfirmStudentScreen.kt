package app.invigilator.ui.linking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

@Composable
fun ConfirmStudentScreen(
    studentName: String,
    studentDateOfBirthMillis: Long?,
    onConfirmed: () -> Unit,
    onNotMyChild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ageText = studentDateOfBirthMillis?.let { dob ->
        val birthDate = Instant.ofEpochMilli(dob).atZone(ZoneId.systemDefault()).toLocalDate()
        val age = Period.between(birthDate, LocalDate.now()).years
        "Age: $age"
    } ?: ""

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "You are about to link:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = studentName,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (ageText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = ageText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Is this your child?",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(onClick = onNotMyChild, modifier = Modifier.weight(1f)) {
                Text("Not my child")
            }
            Spacer(Modifier.width(0.dp))
            Button(onClick = onConfirmed, modifier = Modifier.weight(1f)) {
                Text("Yes, continue")
            }
        }
    }
}
