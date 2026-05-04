# Sprint 3 Phase 2 — Foreground Service and App Detection

Time estimate: 2h Claude Code + 30min testing.

What gets built: SessionMonitorService (foreground service with persistent
notification), USAGE_STATS polling that detects current foreground app and
logs it via Timber, SessionActiveScreen with clock + End button, wiring
from Start tap → service start → SessionActive screen.

What's NOT in this phase: distraction classification (Phase 3), Firestore
session writes (Phase 4), timer expiry/celebration (Phase 4).

═══════════════════════════════════════════════════════════════════════
DELIVERABLES
═══════════════════════════════════════════════════════════════════════

1. New service: SessionMonitorService.kt in
   feature-blocker/src/main/kotlin/app/invigilator/blocker/

2. New repository for session state observation: SessionStateRepository
   (interface in :core, implementation in :core or :feature-blocker)

3. New screens (in app/src/main/kotlin/app/invigilator/ui/session/):
   - SessionActiveScreen.kt + SessionActiveRoute.kt + SessionActiveViewModel.kt

4. Modified:
   - AndroidManifest.xml — add FOREGROUND_SERVICE permission, FOREGROUND_SERVICE_DATA_SYNC permission, POST_NOTIFICATIONS permission, register the service
   - Routes.kt — add Route.SessionActive
   - InvigilatorNavHost.kt — wire SessionActive route, replace toast in StartSession onStart
   - strings.xml — add new strings

═══════════════════════════════════════════════════════════════════════
EXACT SPECIFICATIONS
═══════════════════════════════════════════════════════════════════════

──── AndroidManifest.xml — add to <manifest> ────

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

(PACKAGE_USAGE_STATS is already there from Phase 1.)

Inside <application>:

```xml
<service
    android:name="app.invigilator.blocker.SessionMonitorService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

──── SessionStateRepository (interface in :core) ────

```kotlin
package app.invigilator.core.session

interface SessionStateRepository {
    /** Emits the current active session, or null if no session is active. */
    val activeSession: StateFlow<ActiveSession?>

    fun startSession(sessionType: SessionType, studentUid: String)
    fun endSession()
}

data class ActiveSession(
    val sessionId: String,           // UUID
    val sessionType: SessionType,
    val studentUid: String,
    val startedAtMillis: Long,
)
```

──── SessionStateRepositoryImpl ────

In :core, package app.invigilator.core.session:

```kotlin
@Singleton
internal class SessionStateRepositoryImpl @Inject constructor() : SessionStateRepository {
    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    override val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    override fun startSession(sessionType: SessionType, studentUid: String) {
        _activeSession.value = ActiveSession(
            sessionId = UUID.randomUUID().toString(),
            sessionType = sessionType,
            studentUid = studentUid,
            startedAtMillis = System.currentTimeMillis(),
        )
    }

    override fun endSession() {
        _activeSession.value = null
    }
}
```

Add a @Binds in CoreModule:
```kotlin
@Binds @Singleton
abstract fun bindSessionStateRepository(impl: SessionStateRepositoryImpl): SessionStateRepository
```

──── SessionMonitorService.kt ────

In feature-blocker/src/main/kotlin/app/invigilator/blocker/:

```kotlin
@AndroidEntryPoint
class SessionMonitorService : Service() {

    @Inject lateinit var sessionState: SessionStateRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var lastDetectedPackage: String? = null

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "session_monitor"
        private const val POLL_INTERVAL_MS = 2000L
        const val ACTION_STOP = "app.invigilator.action.STOP_SESSION"
    }

    override fun onCreate() {
        super.onCreate()
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                detectForegroundApp()
                delay(POLL_INTERVAL_MS)
            }
        }
        Timber.d("SessionMonitor: started polling")
    }

    private fun detectForegroundApp() {
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(now - POLL_INTERVAL_MS - 1000, now)
        var lastEventPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastEventPackage = event.packageName
            }
        }
        if (lastEventPackage != null && lastEventPackage != lastDetectedPackage) {
            lastDetectedPackage = lastEventPackage
            Timber.d("SessionMonitor: foreground app changed to $lastEventPackage")
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        sessionState.endSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.d("SessionMonitor: stopped")
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, SessionMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_session_active_title))
            .setContentText(getString(R.string.notification_session_active_body))
            .setSmallIcon(android.R.drawable.ic_menu_view)  // placeholder; design icon later
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, getString(R.string.notification_action_end), stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_session),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_session_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
        scope.cancel()
    }
}
```

──── SessionActiveViewModel.kt ────

```kotlin
@HiltViewModel
class SessionActiveViewModel @Inject constructor(
    private val sessionState: SessionStateRepository,
) : ViewModel() {
    private val _elapsed = MutableStateFlow(0L)
    val state: StateFlow<SessionActiveUiState> = combine(
        sessionState.activeSession,
        _elapsed,
    ) { session, elapsed ->
        SessionActiveUiState(
            isActive = session != null,
            elapsedSeconds = elapsed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionActiveUiState())

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val started = sessionState.activeSession.value?.startedAtMillis ?: continue
                _elapsed.value = (System.currentTimeMillis() - started) / 1000
            }
        }
    }
}

data class SessionActiveUiState(
    val isActive: Boolean = false,
    val elapsedSeconds: Long = 0,
)
```

──── SessionActiveScreen.kt ────

Material3, intentionally minimal. Center column.

- TopAppBar: title "Focus session" — NO back button (back press is intercepted to do nothing during a session; or shows confirm dialog)
- Big clock display (centered, large): "00:24:13" — format MM:SS up to 1 hour, then HH:MM:SS
- Below clock, smaller text: "Stay focused. Tap end when done."
- At bottom: large "End session" button

```kotlin
@Composable
fun SessionActiveScreen(
    state: SessionActiveUiState,
    onEnd: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.session_active_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(64.dp))

            Text(
                text = formatElapsed(state.elapsedSeconds),
                style = MaterialTheme.typography.displayLarge,
                fontSize = 72.sp,
            )

            Text(
                text = stringResource(R.string.session_active_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onEnd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) {
                Text(stringResource(R.string.action_end_session))
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
```

──── SessionActiveRoute.kt ────

```kotlin
@Composable
fun SessionActiveRoute(
    onSessionEnded: () -> Unit,
    viewModel: SessionActiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // When sessionState becomes null (service stopped), navigate away
    LaunchedEffect(state.isActive) {
        if (!state.isActive) onSessionEnded()
    }

    SessionActiveScreen(
        state = state,
        onEnd = {
            // Send STOP intent to service
            val intent = Intent(context, SessionMonitorService::class.java).apply {
                action = SessionMonitorService.ACTION_STOP
            }
            context.startService(intent)
        },
    )

    // Block back press during active session — opt: do nothing
    BackHandler(enabled = state.isActive) { /* swallow */ }
}
```

──── Routes.kt — add ────

```kotlin
@Serializable data object SessionActive : Route
```

──── InvigilatorNavHost.kt — modify ────

Replace the StartSession composable's Phase 1 toast with real session start:

```kotlin
composable<Route.StartSession> {
    val sessionState: SessionStateRepository = hiltViewModel<NavGraphViewModel>().sessionState
    val context = LocalContext.current
    StartSessionRoute(
        onStart = { sessionType ->
            if (permissionChecker.hasUsageStatsPermission()) {
                // Get current student uid from auth (or pass through)
                val studentUid = FirebaseAuth.getInstance().currentUser?.uid
                if (studentUid != null) {
                    sessionState.startSession(sessionType, studentUid)
                    context.startForegroundService(
                        Intent(context, SessionMonitorService::class.java)
                    )
                    navController.navigate(Route.SessionActive) {
                        popUpTo(Route.StudentHome) { inclusive = false }
                    }
                }
            } else {
                navController.navigate(Route.Permissions)
            }
        },
        onBack = { navController.popBackStack() },
    )
}

composable<Route.SessionActive> {
    SessionActiveRoute(
        onSessionEnded = {
            navController.navigate(Route.StudentHome) {
                popUpTo(navController.graph.id) { inclusive = false }
                launchSingleTop = true
            }
        }
    )
}
```

If a NavGraphViewModel pattern doesn't already exist in the project,
inject SessionStateRepository directly via hiltViewModel scoped to
the nav graph, OR pass it from MainActivity through a CompositionLocal.
Use whatever pattern matches the existing code style.

──── strings.xml additions (en) ────

```
session_active_title              → "Focus session"
session_active_subtitle           → "Stay focused. Tap end when done."
action_end_session                → "End session"
notification_channel_session      → "Active session"
notification_channel_session_desc → "Shown while a focus session is in progress"
notification_session_active_title → "Focus session active"
notification_session_active_body  → "Tap to return to Invigilator"
notification_action_end           → "End session"
```

Mark as/hi/bn versions as [DRAFT] same as before.

──── POST_NOTIFICATIONS runtime permission ────

On Android 13 (API 33) and above, POST_NOTIFICATIONS must be requested
at runtime. The cleanest place is right before starting the service.

In InvigilatorNavHost.kt or MainActivity, add a permission launcher:

```kotlin
val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    // Even if denied, we proceed — user can enable later in Settings
}

// Before starting the service, on API 33+:
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    val permission = android.Manifest.permission.POST_NOTIFICATIONS
    if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
        notificationPermissionLauncher.launch(permission)
    }
}
```

This is a fire-and-forget request. If denied, the foreground service
still works on most Android versions; only the visible notification
won't show. Acceptable for Phase 2 — we'll polish this in Sprint 6.

═══════════════════════════════════════════════════════════════════════
TESTS
═══════════════════════════════════════════════════════════════════════

Add unit tests:

- SessionStateRepositoryImplTest:
  - startSession_setsActiveSession (with sessionId, type, studentUid)
  - endSession_clearsActiveSession
  - startSession_overwritesExisting (if a session already active, new one replaces)
  - sessionId_isUnique_acrossCalls

- SessionActiveViewModelTest:
  - elapsedSeconds_increments_when_session_active
  - state_isActive_false_when_no_active_session
  - state_isActive_true_when_session_present

The SessionMonitorService itself is hard to unit-test (Android service
infrastructure). Skip service tests — we'll verify via Logcat manually.

Run: ./gradlew testDebugUnitTest lint
Both must pass.

═══════════════════════════════════════════════════════════════════════
COMMIT AND PUSH (MANDATORY)
═══════════════════════════════════════════════════════════════════════

Commit message: "feat(session): foreground service, app detection, active session screen"

Then: git push origin main

Push is non-negotiable.

═══════════════════════════════════════════════════════════════════════
WHAT THE HUMAN WILL TEST
═══════════════════════════════════════════════════════════════════════

After APK install on phone:

1. Open Logcat in Android Studio. Filter: `SessionMonitor`.
2. Sign in as student (any account that's already permission-granted from Phase 1).
3. Tap Start session → Timed → 25 min → Start.
4. App should navigate to SessionActiveScreen showing 00:00 ticking up.
5. Notification appears: "Focus session active".
6. Press home button — leave Invigilator. Open Chrome (or any other app).
7. Switch to another app, then another.
8. Look at Logcat — should see lines like:
   "SessionMonitor: foreground app changed to com.android.chrome"
   "SessionMonitor: foreground app changed to com.instagram.android"
9. Return to Invigilator (tap notification or icon).
10. SessionActiveScreen still showing, clock has continued counting.
11. Tap "End session" → returns to StudentHome. Notification disappears.
12. In Logcat, see: "SessionMonitor: stopped".
13. Pull down notifications — verify it's actually gone.

Edge cases to test:
- During session, lock screen for 30 seconds, unlock — clock should be
  accurate (no drift), service still running.
- Tap End from the notification — should also work the same as in-app End.

═══════════════════════════════════════════════════════════════════════
CONSTRAINTS
═══════════════════════════════════════════════════════════════════════

- DO NOT classify apps as study/distracting/etc. — that's Phase 3.
- DO NOT write any session data to Firestore — that's Phase 4.
- DO NOT add timer expiry, celebration, or summary screens — Phase 4.
- DO NOT add duration display ("of 25:00") — clock just counts up for now.
- DO NOT show foreground app on screen — Phase 3 (and even then the user-facing UI stays minimal per "soft hide" spec).
- DO NOT modify Firestore rules.
- The placeholder notification icon (android.R.drawable.ic_menu_view) is fine for Phase 2. Real icon design is post-MVP.
