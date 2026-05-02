package app.invigilator.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.invigilator.R

@Composable
fun OnboardingLogoutMenu(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.cd_more_options),
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_logout)) },
                onClick = {
                    menuOpen = false
                    confirmOpen = true
                },
            )
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text(stringResource(R.string.logout_confirm_title)) },
            text = { Text(stringResource(R.string.logout_confirm_onboarding_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmOpen = false
                    onLogout()
                }) {
                    Text(stringResource(R.string.action_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
