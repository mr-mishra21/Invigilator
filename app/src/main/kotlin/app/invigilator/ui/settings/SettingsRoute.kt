package app.invigilator.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import app.invigilator.BuildConfig

private const val FEEDBACK_FORM_URL = "https://forms.gle/YUTgefv3nHKM8ZM98"

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    SettingsScreen(
        state = state,
        onLanguageSelected = viewModel::setLanguage,
        onFeedbackTapped = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(FEEDBACK_FORM_URL))
            context.startActivity(intent)
        },
        onSignOutConfirmed = {
            viewModel.signOut()
            onSignedOut()
        },
        onBack = onBack,
        appVersion = BuildConfig.VERSION_NAME,
    )
}
