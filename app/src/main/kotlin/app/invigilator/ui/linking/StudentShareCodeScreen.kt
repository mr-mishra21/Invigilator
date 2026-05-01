package app.invigilator.ui.linking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Full implementation in Part 3 — stub for Part 1 build.

@Composable
fun StudentShareCodeRoute(
    onNavigateToStudentHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StudentShareCodeScreen(modifier = modifier)
}

@Composable
fun StudentShareCodeScreen(modifier: Modifier = Modifier) {
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
        Text(
            text = "Full implementation in Part 3.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
