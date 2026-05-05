# Sprint 3 Phase 4 — Session Closure: Timer, Persistence, Summary, Celebration

Time estimate: 2.5h Claude Code + 60min testing.

What gets built (in this order, with STOP gates between):
  Part 1: Strict timer for TIMED sessions
  Part 2: Firestore persistence of session summaries
  Part 3: SessionSummaryScreen with detailed + qualitative breakdown
  Part 4: CelebrationScreen for TIMED auto-end
  Part 5: ParentHome shows recent sessions per linked student

This is the final phase of Sprint 3. After this, the product has its
first complete user-visible loop: student starts session → distractions
tracked → session ends → student sees their results → parent sees activity.

═══════════════════════════════════════════════════════════════════════
GLOBAL CONSTRAINTS (apply to every part)
═══════════════════════════════════════════════════════════════════════

- Use `stateIn(SharingStarted.Eagerly)` for ANY new ViewModel that uses
  `combine`, `map`, or any other Flow operator wrapped in stateIn.
- Run `./gradlew lint` BEFORE running tests at the end of each part.
- After each part is complete and committed, STOP and print:
    "PART N COMPLETE. Files modified: [list]. Tests: [count, status].
     Push: [confirmed/failed]. WAITING FOR HUMAN before starting Part N+1."
- Do not push partial work mid-part. One push per completed part.
- All copyright + safety rules from previous sprints still apply.

═══════════════════════════════════════════════════════════════════════
PART 1 — Strict timer for TIMED sessions
═══════════════════════════════════════════════════════════════════════

──── Goal ────

When a Timed session reaches its configured duration, automatically:
  1. Finalize any in-flight distraction event (existing finalizePreviousApp logic)
  2. Stop the foreground service
  3. Set a flag so the next-screen-up logic knows it was an auto-end vs manual

──── Files to modify ────

- feature-blocker/.../SessionMonitorService.kt
- core/src/main/kotlin/.../core/session/SessionStateRepository.kt
- core/src/main/kotlin/.../core/session/SessionStateRepositoryImpl.kt

──── SessionStateRepository interface — add ────

```kotlin
interface SessionStateRepository {
    val activeSession: StateFlow<ActiveSession?>

    /**
     * Set when a session ended. Cleared when a new session starts.
     * Used by Route composables to know how to navigate after end.
     */
    val lastEndReason: StateFlow<SessionEndReason?>

    fun startSession(sessionType: SessionType, studentUid: String)
    fun endSession(reason: SessionEndReason)
    fun clearLastEndReason()
}

enum class SessionEndReason {
    USER_ENDED,         // student tapped End
    TIMER_EXPIRED,      // TIMED session reached duration
}
```

The existing `endSession()` becomes `endSession(reason: SessionEndReason)`.
Update the only existing caller in SessionMonitorService.stopMonitoring()
to pass `SessionEndReason.USER_ENDED`.

──── SessionStateRepositoryImpl — implement ────

```kotlin
private val _lastEndReason = MutableStateFlow<SessionEndReason?>(null)
override val lastEndReason: StateFlow<SessionEndReason?> = _lastEndReason.asStateFlow()

override fun startSession(sessionType: SessionType, studentUid: String) {
    _activeSession.value = ActiveSession(...)
    _lastEndReason.value = null   // clear from any previous session
}

override fun endSession(reason: SessionEndReason) {
    _activeSession.value = null
    _lastEndReason.value = reason
}

override fun clearLastEndReason() {
    _lastEndReason.value = null
}
```

──── SessionMonitorService — modify ────

Add timer-check logic inside the existing polling loop. Don't add a
separate timer (battery-saver hostile); use the polling loop you already
have. The check is cheap.

In `detectForegroundApp()` (or call a new `checkTimerExpiry()` from the
polling loop alongside it):

```kotlin
private fun checkTimerExpiry() {
    val active = sessionState.activeSession.value ?: return
    val type = active.sessionType
    if (type !is SessionType.Timed) return

    val elapsedMs = System.currentTimeMillis() - active.startedAtMillis
    val durationMs = type.durationMinutes * 60_000L

    if (elapsedMs >= durationMs) {
        Timber.d("SessionMonitor: timer expired (${type.durationMinutes}min)")
        timerExpired()
    }
}

private fun timerExpired() {
    finalizePreviousApp(System.currentTimeMillis())
    monitorJob?.cancel()
    sessionState.endSession(SessionEndReason.TIMER_EXPIRED)
    sessionStats.reset()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
}
```

The polling loop becomes:

```kotlin
monitorJob = scope.launch {
    while (isActive) {
        detectForegroundApp()
        checkTimerExpiry()
        delay(POLL_INTERVAL_MS)
    }
}
```

The existing `stopMonitoring()` becomes:

```kotlin
private fun stopMonitoring() {
    finalizePreviousApp(System.currentTimeMillis())
    monitorJob?.cancel()
    sessionState.endSession(SessionEndReason.USER_ENDED)
    sessionStats.reset()  // do NOT reset here — Part 2 needs the stats for Firestore write
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
    Timber.d("SessionMonitor: stopped (user ended)")
}
```

Wait — that comment about `sessionStats.reset()` is WRONG. Read it again.

Correction: keep the reset() calls. We will read the stats BEFORE
ending the session (in Part 2). Part 2 fetches the stats first, then
end is called, which then resets. Order matters and we'll get to it.

For Part 1, just keep `sessionStats.reset()` in both stopMonitoring and
timerExpired. Part 2 will rework this.

──── Tests for Part 1 ────

Update existing SessionStateRepositoryImplTest:
- Test that `endSession(USER_ENDED)` sets lastEndReason to USER_ENDED
- Test that `endSession(TIMER_EXPIRED)` sets lastEndReason to TIMER_EXPIRED
- Test that `startSession` clears lastEndReason
- Test that `clearLastEndReason()` sets it back to null

(Service timer logic is harder to unit-test; we verify on device.)

──── Lint + tests ────

```bash
./gradlew lint
./gradlew testDevDebugUnitTest
```

Both must pass.

──── Commit + push ────

```
fix(session): strict timer for TIMED session auto-end
```

```bash
git push origin main
```

──── STOP GATE ────

Print: "PART 1 COMPLETE. Files modified: [list]. Tests: [count, status].
Push: confirmed. WAITING FOR HUMAN before starting Part 2."

The human will install the APK and verify:
1. Start a 1-minute TIMED session (use 1 min for fast testing — add this
   option temporarily in StartSession if 15 min is the smallest currently;
   otherwise use 15 min and wait).
2. Wait for timer to expire.
3. Logcat should show: "SessionMonitor: timer expired (1min)"
4. SessionMonitorService should fully stop.
5. App's UI is currently on SessionActiveScreen — it will route somewhere
   weird because we haven't built Part 4 (CelebrationScreen) yet. That's
   fine — the human will see the route handling break, which is expected
   at this gate.

═══════════════════════════════════════════════════════════════════════
PART 2 — Firestore persistence of session summaries
═══════════════════════════════════════════════════════════════════════

──── Goal ────

When a session ends (either reason), write a summary doc to
`/users/{studentUid}/sessions/{sessionId}`. The write happens
best-effort — it must not block UI navigation.

──── New files ────

- core/src/main/kotlin/.../core/session/SessionDoc.kt
- core/src/main/kotlin/.../core/session/SessionSummaryRepository.kt
- core/src/main/kotlin/.../core/session/SessionSummaryRepositoryImpl.kt

──── SessionDoc.kt ────

```kotlin
package app.invigilator.core.session

import com.google.firebase.Timestamp

/**
 * Document persisted to /users/{studentUid}/sessions/{sessionId}
 * after a session ends.
 */
data class SessionDoc(
    val sessionId: String = "",
    val studentUid: String = "",
    val startedAt: Timestamp = Timestamp.now(),
    val endedAt: Timestamp = Timestamp.now(),
    val durationSeconds: Long = 0,
    val plannedDurationSeconds: Long = 0,    // 0 if OPEN_ENDED
    val sessionType: String = "",            // "TIMED" or "OPEN_ENDED"
    val endReason: String = "",              // "USER_ENDED" or "TIMER_EXPIRED"
    val distractionCount: Int = 0,
    val distractions: List<DistractionRecord> = emptyList(),
)

data class DistractionRecord(
    val packageName: String = "",
    val category: String = "",   // "DISTRACTING" or "UNKNOWN"
    val dwellSeconds: Long = 0,
)
```

(`DistractionRecord` is the persisted analog of `DistractionEvent` —
strings instead of enums for Firestore-friendliness, dwell time only,
no enter/exit timestamps to keep docs compact.)

──── SessionSummaryRepository.kt ────

```kotlin
package app.invigilator.core.session

interface SessionSummaryRepository {
    /**
     * Best-effort write of session summary to Firestore. Failures are logged
     * but do not throw. The caller should not await this — it returns
     * immediately and the write happens in the background.
     */
    fun saveSession(doc: SessionDoc)

    /**
     * Observe the most recent N sessions for a given student.
     * Used by SessionSummaryScreen and ParentHome.
     */
    fun observeRecentSessions(studentUid: String, limit: Int = 10): Flow<List<SessionDoc>>
}
```

──── SessionSummaryRepositoryImpl.kt ────

```kotlin
package app.invigilator.core.session

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.snapshots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SessionSummaryRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : SessionSummaryRepository {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun saveSession(doc: SessionDoc) {
        ioScope.launch {
            try {
                firestore
                    .collection("users")
                    .document(doc.studentUid)
                    .collection("sessions")
                    .document(doc.sessionId)
                    .set(doc)
                    .await()
                Timber.d("SessionSummary: persisted ${doc.sessionId}")
            } catch (e: Exception) {
                Timber.e(e, "SessionSummary: write failed for ${doc.sessionId}")
                // Best-effort. We do not retry; could enqueue for later in Sprint 6.
            }
        }
    }

    override fun observeRecentSessions(studentUid: String, limit: Int): Flow<List<SessionDoc>> {
        return firestore
            .collection("users")
            .document(studentUid)
            .collection("sessions")
            .orderBy("endedAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .snapshots()
            .map { snap -> snap.toObjects(SessionDoc::class.java) }
            .catch { e ->
                Timber.e(e, "SessionSummary: observe failed for $studentUid")
                emit(emptyList())
            }
    }
}
```

(`.snapshots()` is from `firebase-firestore-ktx`. If that import isn't
available in the project, use the callback-based listener wrapped in
`callbackFlow` — pattern already exists in LinkingRepositoryImpl.)

──── SessionMonitorService.kt — wire the write ────

Inject the new repository:
```kotlin
@Inject lateinit var sessionSummaryRepo: SessionSummaryRepository
```

Refactor `stopMonitoring()` and `timerExpired()` to share end logic:

```kotlin
private fun stopMonitoring() = endSession(SessionEndReason.USER_ENDED)
private fun timerExpired() = endSession(SessionEndReason.TIMER_EXPIRED)

private fun endSession(reason: SessionEndReason) {
    val now = System.currentTimeMillis()
    finalizePreviousApp(now)

    // Capture state BEFORE clearing
    val active = sessionState.activeSession.value
    val stats = sessionStats.stats.value

    // Write to Firestore (best-effort, doesn't block)
    if (active != null) {
        val doc = buildSessionDoc(active, stats, now, reason)
        sessionSummaryRepo.saveSession(doc)
    }

    // Now clear local state
    monitorJob?.cancel()
    sessionState.endSession(reason)
    sessionStats.reset()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
    Timber.d("SessionMonitor: stopped ($reason)")
}

private fun buildSessionDoc(
    active: ActiveSession,
    stats: SessionStats,
    endedAtMillis: Long,
    reason: SessionEndReason,
): SessionDoc {
    val durationSeconds = (endedAtMillis - active.startedAtMillis) / 1000
    val plannedSeconds = when (val t = active.sessionType) {
        is SessionType.Timed -> t.durationMinutes * 60L
        SessionType.OpenEnded -> 0L
    }
    val typeString = when (active.sessionType) {
        is SessionType.Timed -> "TIMED"
        SessionType.OpenEnded -> "OPEN_ENDED"
    }
    return SessionDoc(
        sessionId = active.sessionId,
        studentUid = active.studentUid,
        startedAt = Timestamp(active.startedAtMillis / 1000, 0),
        endedAt = Timestamp(endedAtMillis / 1000, 0),
        durationSeconds = durationSeconds,
        plannedDurationSeconds = plannedSeconds,
        sessionType = typeString,
        endReason = reason.name,
        distractionCount = stats.distractionCount,
        distractions = stats.distractionEvents.map {
            DistractionRecord(
                packageName = it.packageName,
                category = it.category.name,
                dwellSeconds = it.dwellSeconds,
            )
        },
    )
}
```

──── Firestore security rules — add ────

In `firestore.rules`, add inside the `match /databases/{database}/documents`
block:

```
match /users/{studentUid}/sessions/{sessionId} {
  // Student writes their own session docs.
  allow create: if request.auth != null && request.auth.uid == studentUid;

  // No edits or deletes — sessions are immutable history.
  allow update, delete: if false;

  // Student reads own. Linked parents read.
  allow read: if request.auth != null && (
    request.auth.uid == studentUid ||
    isLinkedParentOf(request.auth.uid, studentUid)
  );
}
```

The `isLinkedParentOf` function already exists from Sprint 2's rules.

──── CoreModule.kt — add binding ────

```kotlin
@Binds
@Singleton
abstract fun bindSessionSummaryRepository(
    impl: SessionSummaryRepositoryImpl
): SessionSummaryRepository
```

──── Tests for Part 2 ────

SessionSummaryRepositoryImplTest:
- This is hard to unit-test without Firestore emulator. Mock the
  FirebaseFirestore reference and verify the chained calls (`.collection(),
  .document(), .collection(), .document(), .set()`) are invoked with the
  expected arguments. MockK can do this with `verify { ... }`.
- Test that observeRecentSessions returns empty list on Firestore exception.

If the mocking gets too painful, skip the unit test — we verify the
write on device via Firestore Console.

──── Lint + tests ────

```bash
./gradlew lint
./gradlew testDevDebugUnitTest
```

──── Commit + push ────

```
feat(session): persist session summary to Firestore on end
```

```bash
git push origin main
```

──── STOP GATE ────

Print: "PART 2 COMPLETE. Files modified: [list]. Tests: [count, status].
Push: confirmed. WAITING FOR HUMAN before starting Part 3."

Manual deployment for human:
```bash
firebase deploy --only firestore:rules
```

Human will verify:
1. Start a session, end it manually after a few minutes.
2. Open Firestore Console → /users/{studentUid}/sessions/.
3. A new document should exist with correct fields (sessionId, durations,
   distractionCount, distractions array).
4. Logcat should show: "SessionSummary: persisted [sessionId]"

═══════════════════════════════════════════════════════════════════════
PART 3 — SessionSummaryScreen with detailed + qualitative breakdown
═══════════════════════════════════════════════════════════════════════

──── Goal ────

After any session ends, navigate to SessionSummaryScreen showing:
  - Headline: "X minute session • Y distractions"
  - Verdict: "Excellent focus" / "Good focus" / "Some distractions" / "Lots of distractions"
  - Per-app breakdown: each distraction event with app name and dwell time
  - Two buttons: "Done" (back to StudentHome) and "Start another session"

The verdict thresholds (sprint 6+ tunables):
  0 distractions → "Excellent focus"
  1-2 distractions → "Good focus"
  3-5 distractions → "Some distractions"
  6+ distractions → "Lots of distractions"

──── New files ────

- app/src/main/kotlin/app/invigilator/ui/session/SessionSummaryScreen.kt
- app/src/main/kotlin/app/invigilator/ui/session/SessionSummaryRoute.kt
- app/src/main/kotlin/app/invigilator/ui/session/SessionSummaryViewModel.kt
- app/src/main/kotlin/app/invigilator/util/AppNameResolver.kt — utility to
  turn "com.google.android.youtube" into "YouTube" using PackageManager.

──── AppNameResolver.kt ────

```kotlin
package app.invigilator.util

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNameResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun resolveDisplayName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.')
                .replaceFirstChar { it.uppercaseChar() }
        }
    }
}
```

──── SessionSummaryViewModel.kt ────

```kotlin
@HiltViewModel
class SessionSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    summaryRepo: SessionSummaryRepository,
    private val appNameResolver: AppNameResolver,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>("sessionId") ?: ""
    private val studentUid: String = savedStateHandle.get<String>("studentUid") ?: ""

    val state: StateFlow<SessionSummaryUiState> = summaryRepo
        .observeRecentSessions(studentUid, limit = 10)
        .map { sessions ->
            val match = sessions.firstOrNull { it.sessionId == sessionId }
            if (match == null) {
                SessionSummaryUiState(loading = true)
            } else {
                SessionSummaryUiState(
                    loading = false,
                    durationMinutes = (match.durationSeconds / 60).toInt(),
                    distractionCount = match.distractionCount,
                    verdict = computeVerdict(match.distractionCount),
                    breakdown = match.distractions.map { d ->
                        BreakdownRow(
                            displayName = appNameResolver.resolveDisplayName(d.packageName),
                            dwellSeconds = d.dwellSeconds,
                        )
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionSummaryUiState(loading = true))

    private fun computeVerdict(count: Int): Verdict = when {
        count == 0 -> Verdict.EXCELLENT
        count in 1..2 -> Verdict.GOOD
        count in 3..5 -> Verdict.SOME
        else -> Verdict.LOTS
    }
}

data class SessionSummaryUiState(
    val loading: Boolean = true,
    val durationMinutes: Int = 0,
    val distractionCount: Int = 0,
    val verdict: Verdict = Verdict.EXCELLENT,
    val breakdown: List<BreakdownRow> = emptyList(),
)

data class BreakdownRow(
    val displayName: String,
    val dwellSeconds: Long,
)

enum class Verdict { EXCELLENT, GOOD, SOME, LOTS }
```

──── SessionSummaryScreen.kt ────

Material3 layout:
- TopAppBar: title "Session summary"
- Card 1 (centered, large): "X-minute session"
- Card 2 (verdict): big text, color-coded
    - EXCELLENT: green checkmark + "Excellent focus"
    - GOOD: blue check + "Good focus"
    - SOME: orange + "Some distractions"
    - LOTS: red warning + "Lots of distractions"
- Section header: "Distractions during this session"
- LazyColumn of BreakdownRows: each shows "App name — Xs"
  - If breakdown is empty, show "Great — no distractions! 🎉"
- Bottom buttons:
    - "Start another session" (primary) → navigate to StartSession
    - "Done" (text button) → pop to StudentHome

If `state.loading` is true, show a CircularProgressIndicator centered.
The state will become non-loading when the Firestore listener delivers
the just-written session doc (usually within 1-3 seconds).

──── Routes.kt — add ────

```kotlin
@Serializable
data class SessionSummary(
    val sessionId: String,
    val studentUid: String,
) : Route
```

──── InvigilatorNavHost.kt — modify ────

In the SessionActiveRoute composable's `onSessionEnded` lambda, navigate
to SessionSummary instead of going back to StudentHome:

```kotlin
composable<Route.SessionActive> {
    SessionActiveRoute(
        onSessionEnded = { sessionId, studentUid ->
            navController.navigate(Route.SessionSummary(sessionId, studentUid)) {
                popUpTo(Route.StudentHome) { inclusive = false }
            }
        }
    )
}

composable<Route.SessionSummary> { backStackEntry ->
    val args = backStackEntry.toRoute<Route.SessionSummary>()
    SessionSummaryRoute(
        sessionId = args.sessionId,
        studentUid = args.studentUid,
        onStartAnother = {
            navController.navigate(Route.StartSession) {
                popUpTo(Route.StudentHome) { inclusive = false }
            }
        },
        onDone = {
            navController.navigate(Route.StudentHome) {
                popUpTo(Route.StudentHome) { inclusive = true }
            }
        }
    )
}
```

For `SessionActiveRoute` to know the sessionId+studentUid at end time,
it needs to read them from `sessionState.activeSession` BEFORE the
session ends. Add a remembered value and capture them on entry:

```kotlin
@Composable
fun SessionActiveRoute(
    onSessionEnded: (sessionId: String, studentUid: String) -> Unit,
    viewModel: SessionActiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var sessionContext by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(state.isActive) {
        // Capture context the moment the session is active
        val active = viewModel.activeSessionSnapshot()  // add to VM
        if (active != null && sessionContext == null) {
            sessionContext = active.sessionId to active.studentUid
        }
        // When session becomes inactive, navigate
        if (!state.isActive && sessionContext != null) {
            val (sid, uid) = sessionContext!!
            onSessionEnded(sid, uid)
        }
    }

    SessionActiveScreen(state, onEnd = { ... })
    BackHandler(enabled = state.isActive) {}
}
```

Add `fun activeSessionSnapshot(): ActiveSession? = sessionState.activeSession.value`
to SessionActiveViewModel.

──── strings.xml — add (en, with [DRAFT] for as/hi/bn) ────

```
session_summary_title             → "Session summary"
session_summary_x_minute_session  → "%d-minute session"
verdict_excellent                 → "Excellent focus"
verdict_good                      → "Good focus"
verdict_some                      → "Some distractions"
verdict_lots                      → "Lots of distractions"
session_summary_distractions_header → "Distractions during this session"
session_summary_no_distractions   → "Great — no distractions!"
session_summary_dwell_format      → "%1$s — %2$ds"
action_start_another              → "Start another session"
action_done                       → "Done"
```

──── Tests for Part 3 ────

SessionSummaryViewModelTest:
- loading_state_shown_when_session_not_yet_in_observed_results
- verdict_excellent_when_zero_distractions
- verdict_good_when_one_or_two
- verdict_some_when_three_to_five
- verdict_lots_when_six_or_more
- breakdown_resolves_app_names

──── Lint + tests + commit + push ────

Same pattern. Commit message:
```
feat(session): summary screen with verdict and per-app breakdown
```

──── STOP GATE ────

"PART 3 COMPLETE..." Same human verification: end a session, see the
summary screen with the right verdict and breakdown. The route should
land on SessionSummary instead of going back to StudentHome.

═══════════════════════════════════════════════════════════════════════
PART 4 — CelebrationScreen for TIMED auto-end
═══════════════════════════════════════════════════════════════════════

──── Goal ────

When a TIMED session auto-ends because the timer expired, show a brief
celebration screen for ~3 seconds before transitioning to the
SessionSummary. The screen should feel like a small reward.

──── New files ────

- app/src/main/kotlin/app/invigilator/ui/session/CelebrationScreen.kt
- (No separate Route + ViewModel — this is a simple stateless screen)

──── CelebrationScreen.kt ────

```kotlin
@Composable
fun CelebrationScreen(
    onContinue: () -> Unit,
    durationMinutes: Int,
) {
    LaunchedEffect(Unit) {
        delay(3_000)
        onContinue()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "🎉",
                fontSize = 96.sp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.celebration_done, durationMinutes),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.celebration_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
```

──── strings.xml ────

```
celebration_done       → "%d-minute focus session done!"
celebration_subtitle   → "Great work staying focused."
```

──── Routing logic — modify InvigilatorNavHost ────

The Celebration is shown ONLY when `lastEndReason == TIMER_EXPIRED`.
When the SessionActiveRoute's session-ended LaunchedEffect fires, check
the reason and route accordingly:

```kotlin
LaunchedEffect(state.isActive) {
    if (!state.isActive && sessionContext != null) {
        val (sid, uid) = sessionContext!!
        val reason = sessionState.lastEndReason.value
        if (reason == SessionEndReason.TIMER_EXPIRED) {
            onTimerExpired(sid, uid, plannedMinutes)  // routes to celebration
        } else {
            onSessionEnded(sid, uid)  // routes directly to summary
        }
    }
}
```

Add Celebration to Routes.kt:
```kotlin
@Serializable
data class Celebration(
    val sessionId: String,
    val studentUid: String,
    val durationMinutes: Int,
) : Route
```

In NavHost:
```kotlin
composable<Route.Celebration> { backStackEntry ->
    val args = backStackEntry.toRoute<Route.Celebration>()
    CelebrationScreen(
        durationMinutes = args.durationMinutes,
        onContinue = {
            navController.navigate(Route.SessionSummary(args.sessionId, args.studentUid)) {
                popUpTo(Route.StudentHome) { inclusive = false }
                launchSingleTop = true
            }
        }
    )
}
```

The plannedMinutes value: at session start, capture the configured
duration. Add it to ActiveSession:

```kotlin
data class ActiveSession(
    val sessionId: String,
    val sessionType: SessionType,
    val studentUid: String,
    val startedAtMillis: Long,
    val plannedDurationMinutes: Int,  // 0 for OPEN_ENDED
)
```

──── Tests for Part 4 ────

No new tests required — CelebrationScreen is stateless and visual.
Manual verification on device.

──── Lint + tests + commit + push ────

```
feat(session): celebration screen on TIMED auto-end
```

──── STOP GATE ────

"PART 4 COMPLETE..." Human verifies: 1-minute TIMED session expires,
sees celebration for 3 seconds, then summary screen.

═══════════════════════════════════════════════════════════════════════
PART 5 — ParentHome shows recent sessions per linked student
═══════════════════════════════════════════════════════════════════════

──── Goal ────

In ParentHomeScreen, under each linked student, show a small line:
"3 sessions today • last 12m ago" or "No sessions yet".

──── Files to modify ────

- app/src/main/kotlin/app/invigilator/ui/parent/ParentHomeViewModel.kt
- app/src/main/kotlin/app/invigilator/ui/parent/ParentHomeScreen.kt

──── ParentHomeViewModel — modify ────

For each linked student, also observe their recent sessions. Combine
into a unified state:

```kotlin
data class LinkedStudentRow(
    val studentUid: String,
    val displayName: String,
    val accountStatus: String,
    val sessionsToday: Int,
    val lastSessionMinutesAgo: Long?,  // null if no sessions yet
)
```

Use `combine` over the linkedStudents flow + each student's sessions
flow. The implementation will be a `flatMapLatest` chain. Keep it
straightforward — for MVP, observe up to 10 recent sessions per
student and filter "today" client-side using LocalDate.

──── ParentHomeScreen — modify ────

Each student row in the LazyColumn now shows two lines instead of one:
```
• Aarav Mishra                     [active]
  3 sessions today • last 12m ago
```

If sessionsToday == 0:
```
• Aarav Mishra                     [active]
  No sessions today
```

If lastSessionMinutesAgo is null entirely (zero history):
```
• Aarav Mishra                     [active]
  No sessions yet
```

──── Tests for Part 5 ────

Update ParentHomeViewModelTest:
- linkedStudentRow_shows_zero_sessions_when_history_empty
- linkedStudentRow_counts_only_today
- linkedStudentRow_computes_minutes_ago_correctly

──── Lint + tests + commit + push ────

```
feat(parent): show session activity per linked student in ParentHome
```

──── FINAL STOP GATE ────

"PART 5 COMPLETE. SPRINT 3 DONE. Files modified across all 5 parts:
[list]. Tests: [total count, status]. Push: confirmed for each part.
Sprint 3 complete — full session loop is now functional end-to-end."

The human will verify the full Sprint 3 loop:
1. Two phones (student + parent)
2. Student starts a session, distracts a couple times, ends
3. Student sees SessionSummary with verdict and breakdown
4. Parent sees session count and "Xm ago" on their ParentHome

═══════════════════════════════════════════════════════════════════════
GLOBAL CONSTRAINTS REMINDER
═══════════════════════════════════════════════════════════════════════

- Stop after EACH PART. Do not roll multiple parts into one commit.
- Use stateIn(SharingStarted.Eagerly) on every new ViewModel using combine/map.
- Run `./gradlew lint` BEFORE running tests, in every part.
- Do NOT touch consent/linking/auth code.
- Do NOT add bullet points to user-facing text — use sentences.
- Best-effort writes only — no .await() that blocks UI navigation.
- Push after EACH part. The OneDrive disaster will not recur.
