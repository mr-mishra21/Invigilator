package app.invigilator.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.invigilator.core.user.UserRole

@Composable
fun RoleSelectRoute(
    onRoleSelected: (role: String) -> Unit,
    onboardingViewModel: OnboardingViewModel,
) {
    val state by onboardingViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.role) {
        if (state.role.isNotBlank()) onRoleSelected(state.role)
    }

    RoleSelectScreen(
        onRoleSelected = { role ->
            onboardingViewModel.onEvent(OnboardingEvent.RoleSelected(role))
        },
    )
}

@Composable
fun RoleSelectScreen(
    onRoleSelected: (role: String) -> Unit,
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
            text = "Welcome to Invigilator",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "How will you use this app?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { onRoleSelected(UserRole.STUDENT.firestoreValue) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("I am a student")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onRoleSelected(UserRole.PARENT.firestoreValue) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("I am a parent")
        }
    }
}
