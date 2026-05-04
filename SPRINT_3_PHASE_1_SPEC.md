# Sprint 3 Phase 1 — Permissions and Start Session UI

Time estimate: 1.5h Claude Code + 30min testing.

What gets built: Permissions screen, Start Session screen with duration
picker, routing from StudentHome. NO monitoring logic yet (that's Phase 2).

═══════════════════════════════════════════════════════════════════════
DELIVERABLES (just files to create/modify)
═══════════════════════════════════════════════════════════════════════

1. New screens (in app/src/main/kotlin/app/invigilator/ui/session/):
   - PermissionsScreen.kt + PermissionsRoute.kt + PermissionsViewModel.kt
   - StartSessionScreen.kt + StartSessionRoute.kt + StartSessionViewModel.kt

2. Modified:
   - StudentHomeScreen.kt — replace placeholder "Start session" button
   - Routes.kt — add 2 new routes
   - InvigilatorNavHost.kt — wire 2 new routes

3. SessionType enum (in core/src/main/kotlin/.../core/session/):
   - SessionType.kt with: TIMED(durationMinutes), OPEN_ENDED

4. PermissionChecker utility (in core/src/main/kotlin/.../core/permissions/):
   - PermissionChecker.kt with hasUsageStatsPermission() function

═══════════════════════════════════════════════════════════════════════
EXACT SPECIFICATIONS
═══════════════════════════════════════════════════════════════════════

──── SessionType.kt ────

```kotlin
sealed class SessionType {
    data class Timed(val durationMinutes: Int) : SessionType()
    data object OpenEnded : SessionType()
}
```

──── PermissionChecker.kt ────

```kotlin
class PermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageStatsSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
```

Add @Provides binding in CoreModule (or wherever Hilt singletons live).

──── Routes.kt — add ────

```kotlin
@Serializable data object Permissions : Route
@Serializable data object StartSession : Route
```

──── PermissionsViewModel.kt ────

```kotlin
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val permissionChecker: PermissionChecker,
) : ViewModel() {
    private val _state = MutableStateFlow(PermissionsUiState())
    val state: StateFlow<PermissionsUiState> = _state.asStateFlow()

    init { refresh() }

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

data class PermissionsUiState(val hasPermission: Boolean = false)

sealed interface PermissionsEvent {
    data object GrantClicked : PermissionsEvent
    data object Refresh : PermissionsEvent
}
```

──── PermissionsScreen.kt ────

Material3 layout. Center column. Top: TopAppBar with back arrow.

Body content:
- Icon (Icons.Default.Security or similar) — large, centered
- Title: "Usage access required" (use stringResource)
- Body text explaining why:
  "Invigilator needs to know when you switch apps so it can help you
   stay focused. Tap the button below, find Invigilator in the list,
   and turn on usage access."
- Status row:
  - If hasPermission: green check icon + "Permission granted" text
  - If !hasPermission: gray circle + "Permission not granted" text
- Button: "Open settings" — calls onEvent(GrantClicked)
- Below button (only when hasPermission): "Continue" button — calls
  onContinue() callback

──── PermissionsRoute.kt ────

```kotlin
@Composable
fun PermissionsRoute(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh permission state when screen returns to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(PermissionsEvent.Refresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PermissionsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onContinue = onContinue,
        onBack = onBack,
    )
}
```

The lifecycle refresh is critical — when the user returns from Settings,
we must re-check the permission state.

──── StartSessionViewModel.kt ────

```kotlin
@HiltViewModel
class StartSessionViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(StartSessionUiState())
    val state: StateFlow<StartSessionUiState> = _state.asStateFlow()

    fun onEvent(event: StartSessionEvent) {
        when (event) {
            is StartSessionEvent.ModeChanged ->
                _state.update { it.copy(mode = event.mode) }
            is StartSessionEvent.DurationChanged ->
                _state.update { it.copy(selectedDurationMinutes = event.minutes) }
            StartSessionEvent.StartClicked -> { /* handled by Route */ }
        }
    }

    fun buildSessionType(): SessionType = when (state.value.mode) {
        SessionMode.TIMED -> SessionType.Timed(state.value.selectedDurationMinutes)
        SessionMode.OPEN -> SessionType.OpenEnded
    }
}

enum class SessionMode { TIMED, OPEN }

data class StartSessionUiState(
    val mode: SessionMode = SessionMode.TIMED,
    val selectedDurationMinutes: Int = 25,
)

sealed interface StartSessionEvent {
    data class ModeChanged(val mode: SessionMode) : StartSessionEvent
    data class DurationChanged(val minutes: Int) : StartSessionEvent
    data object StartClicked : StartSessionEvent
}
```

──── StartSessionScreen.kt ────

Top: TopAppBar with back arrow + title "Start session"

Body:
- SegmentedButton or two big toggle Cards:
  - "Timed session"
  - "Open-ended"
- If TIMED selected, show duration picker:
  - Row of FilterChips: "15 min" "25 min" "45 min" "60 min" "90 min"
  - Highlight the selected one
- Bottom: large primary "Start" button — calls onEvent(StartClicked)
  - For Phase 1, tapping Start just navigates to a placeholder
    "Session would start here" screen (or back to StudentHome with a toast).
    Phase 2 will replace with real session start.

──── StartSessionRoute.kt ────

```kotlin
@Composable
fun StartSessionRoute(
    onStart: (SessionType) -> Unit,  // Phase 2 wires real start
    onBack: () -> Unit,
    viewModel: StartSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    StartSessionScreen(
        state = state,
        onEvent = { event ->
            if (event is StartSessionEvent.StartClicked) {
                onStart(viewModel.buildSessionType())
            } else {
                viewModel.onEvent(event)
            }
        },
        onBack = onBack,
    )
}
```

──── StudentHomeScreen.kt — modify ────

Find the existing "Start session" placeholder area. Replace the button's
onClick to navigate. The actual nav lambda is wired in InvigilatorNavHost.

```kotlin
// In StudentHomeScreen signature, ensure there is:
onStartSession: () -> Unit,

// Wire the button:
Button(onClick = onStartSession) { Text(stringResource(R.string.action_start_session)) }
```

──── InvigilatorNavHost.kt — add ────

```kotlin
composable<Route.StartSession> {
    StartSessionRoute(
        onStart = { sessionType ->
            // Phase 1: just check permission and route accordingly
            if (permissionChecker.hasUsageStatsPermission()) {
                // Phase 2: start real session. For now, toast.
                Toast.makeText(context, "Session would start (Phase 2)", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            } else {
                navController.navigate(Route.Permissions)
            }
        },
        onBack = { navController.popBackStack() },
    )
}

composable<Route.Permissions> {
    PermissionsRoute(
        onContinue = {
            // Return to StartSession, which will now find permission granted
            navController.popBackStack()
        },
        onBack = { navController.popBackStack() },
    )
}
```

In the StudentHomeRoute composable composition, wire onStartSession:
```kotlin
onStartSession = { navController.navigate(Route.StartSession) }
```

You'll need to inject PermissionChecker into the NavHost composable.
Use hiltViewModel pattern or pass via a lambda from MainActivity.

═══════════════════════════════════════════════════════════════════════
STRINGS (add to strings.xml in en, as, hi, bn)
═══════════════════════════════════════════════════════════════════════

```
permissions_title          → "Usage access required"
permissions_body           → "Invigilator needs to know when you switch apps so it can help you stay focused. Tap the button below, find Invigilator in the list, and turn on usage access."
permissions_granted        → "Permission granted"
permissions_not_granted    → "Permission not granted"
action_open_settings       → "Open settings"
action_continue            → "Continue"
start_session_title        → "Start session"
start_session_timed        → "Timed session"
start_session_open         → "Open-ended"
duration_15                → "15 min"
duration_25                → "25 min"
duration_45                → "45 min"
duration_60                → "60 min"
duration_90                → "90 min"
action_start               → "Start"
action_start_session       → "Start session"
```

For as, hi, bn — mark as [DRAFT] if uncertain. Same pattern as Sprint 2.

═══════════════════════════════════════════════════════════════════════
ANDROIDMANIFEST.XML — add permission
═══════════════════════════════════════════════════════════════════════

In app/src/main/AndroidManifest.xml, inside <manifest> tag (NOT inside
<application>):

```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
```

Make sure tools namespace is declared at the manifest root:
```xml
xmlns:tools="http://schemas.android.com/tools"
```

═══════════════════════════════════════════════════════════════════════
TESTS
═══════════════════════════════════════════════════════════════════════

Add minimal unit tests:
- PermissionsViewModelTest:
  - hasPermission_initial_false (mock checker returns false)
  - hasPermission_after_refresh_true (mock returns true on refresh)
- StartSessionViewModelTest:
  - default_mode_is_timed_25min
  - mode_change_to_open_clears_duration_relevance
  - buildSessionType_timed_returns_correct_duration
  - buildSessionType_open_returns_OpenEnded

Run: ./gradlew testDebugUnitTest lint
Both must pass.

═══════════════════════════════════════════════════════════════════════
COMMIT AND PUSH (MANDATORY)
═══════════════════════════════════════════════════════════════════════

Commit message: "feat(session): permissions screen and start session UI"

Then: git push origin main

The push is non-negotiable. Past sprint had a 2-day OneDrive disaster
caused by 8 unpushed commits.

═══════════════════════════════════════════════════════════════════════
WHAT THE HUMAN WILL TEST
═══════════════════════════════════════════════════════════════════════

After APK install:

1. Sign in as student (any age, any account state)
2. Land on StudentHome
3. Tap "Start session" → routes to StartSessionScreen
4. Toggle between Timed and Open-ended → UI updates correctly
5. Pick different durations → chip selection updates
6. Tap Start → first time, routes to PermissionsScreen
7. Tap "Open settings" → Android Settings opens to Usage Access page
8. Find Invigilator, toggle ON, return to app
9. PermissionsScreen now shows green check + "Continue" button
10. Tap Continue → returns to StartSessionScreen
11. Tap Start → toast appears "Session would start (Phase 2)"

═══════════════════════════════════════════════════════════════════════
CONSTRAINTS
═══════════════════════════════════════════════════════════════════════

- DO NOT build the foreground service (Phase 2)
- DO NOT build distraction detection (Phase 3)
- DO NOT build session timer or summary (Phase 4)
- DO NOT add the "End session" button (no session is starting yet)
- DO NOT modify Firestore schema or rules
- DO NOT touch consent or linking code
- Keep the placeholder Toast on Start tap. It's not a hack — it's the
  Phase 1 boundary.
