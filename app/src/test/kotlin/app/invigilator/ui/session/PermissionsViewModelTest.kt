package app.invigilator.ui.session

import app.invigilator.core.permissions.PermissionChecker
import app.invigilator.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PermissionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val permissionChecker: PermissionChecker = mockk()

    private fun viewModel() = PermissionsViewModel(permissionChecker)

    @Test
    fun `hasPermission is false on init when checker returns false`() {
        every { permissionChecker.hasUsageStatsPermission() } returns false
        every { permissionChecker.hasNotificationPermission() } returns false

        val vm = viewModel()

        assertFalse(vm.state.value.hasPermission)
    }

    @Test
    fun `hasPermission becomes true after refresh when checker returns true`() {
        every { permissionChecker.hasUsageStatsPermission() } returns false
        every { permissionChecker.hasNotificationPermission() } returns false
        val vm = viewModel()
        assertFalse(vm.state.value.hasPermission)

        every { permissionChecker.hasUsageStatsPermission() } returns true
        vm.onEvent(PermissionsEvent.Refresh)

        assertTrue(vm.state.value.hasPermission)
    }
}
