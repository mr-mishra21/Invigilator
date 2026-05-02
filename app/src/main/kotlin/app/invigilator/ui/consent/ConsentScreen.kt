package app.invigilator.ui.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ConsentRoute(
    onComplete: (consentId: String) -> Unit,
    viewModel: ConsentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isComplete, state.consentId) {
        if (state.isComplete) {
            val id = state.consentId ?: return@LaunchedEffect
            viewModel.clearComplete()
            onComplete(id)
        }
    }

    ConsentScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@Composable
fun ConsentScreen(
    state: ConsentUiState,
    onEvent: (ConsentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Detect when user has scrolled to the last item in the LazyColumn.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            lastVisible != null && lastVisible.index == info.totalItemsCount - 1
        }
    }

    // Fire ScrolledToEnd event once (atBottom is sticky — once true, stays true).
    LaunchedEffect(atBottom) {
        if (atBottom) {
            snapshotFlow { atBottom }.collect { reached ->
                if (reached) onEvent(ConsentEvent.ScrolledToEnd)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Language selector row — show all 4 even if device locale matches
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (lang in ConsentViewModel.SUPPORTED_LANGUAGES) {
                FilterChip(
                    selected = state.language == lang,
                    onClick = { onEvent(ConsentEvent.LanguageChanged(lang)) },
                    label = { Text(lang.uppercase()) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Consent text in LazyColumn
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            item {
                Text(
                    text = state.consentText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Signature field
        OutlinedTextField(
            value = state.signature,
            onValueChange = { onEvent(ConsentEvent.SignatureChanged(it)) },
            label = { Text("Full name (your e-signature)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        if (!state.hasScrolledToEnd) {
            Text(
                text = "Scroll to the end to enable \"I agree\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Error message (inline, red — visible while looking at the button)
        if (state.error != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { onEvent(ConsentEvent.AgreedTapped) },
            enabled = state.canAgree,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("I agree")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
