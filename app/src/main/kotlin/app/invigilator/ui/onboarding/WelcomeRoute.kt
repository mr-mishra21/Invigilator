package app.invigilator.ui.onboarding

import androidx.compose.runtime.Composable

@Composable
fun WelcomeRoute(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    WelcomeScreen(
        onSignIn = onSignIn,
        onCreateAccount = onCreateAccount,
    )
}
