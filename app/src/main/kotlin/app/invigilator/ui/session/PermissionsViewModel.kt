package app.invigilator.ui.session

import androidx.lifecycle.ViewModel
import app.invigilator.core.permissions.PermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PermissionsUiState(val hasPermission: Boolean = false)

sealed interface PermissionsEvent {
    data object GrantClicked : PermissionsEvent
    data object Refresh : PermissionsEvent
}

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val permissionChecker: PermissionChecker,
) : ViewModel() {

    private val _state = MutableStateFlow(PermissionsUiState())
    val state: StateFlow<PermissionsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onEvent(event: PermissionsEvent) {
        when (event) {
            PermissionsEvent.GrantClicked -> permissionChecker.openUsageStatsSettings()
            PermissionsEvent.Refresh -> refresh()
        }
    }

    private fun refresh() {
        _state.update { it.copy(hasPermission = permissionChecker.hasUsageStatsPermission()) }
    }
}
