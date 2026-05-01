package app.invigilator.ui.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SplashRoute(
    onNavigateToOnboarding: () -> Unit,
    onNavigateToParentHome: () -> Unit,
    onNavigateToStudentHome: () -> Unit,
    onNavigateToAdultStudentConsent: () -> Unit,
    onNavigateToStudentShareCode: () -> Unit,
    onNavigateToParentConsent: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        when (state.destination) {
            SplashDestination.Onboarding             -> onNavigateToOnboarding()
            SplashDestination.ParentHome             -> onNavigateToParentHome()
            SplashDestination.StudentHome            -> onNavigateToStudentHome()
            SplashDestination.AdultStudentConsent    -> onNavigateToAdultStudentConsent()
            SplashDestination.StudentShareCodeResume -> onNavigateToStudentShareCode()
            SplashDestination.ParentConsentResume    -> onNavigateToParentConsent()
            null                                     -> Unit
        }
    }

    SplashScreen()
}

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
